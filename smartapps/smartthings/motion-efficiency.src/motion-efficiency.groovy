/**
 *  Motion Efficiency
 *
 *  Copyright 2019 Matt
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "Motion Efficiency",
    namespace: "smartthings",
    author: "Matt",
    description: "Monitor Ecobee remote motion sensors to increase energy efficiency.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Allstate/motion_detected.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Allstate/motion_detected@2x.png") {
    appSetting "notification_motions"
    appSetting "notification_recipients"
}

preferences {
	section("Choose the motion sensors you'd like to monitor.") {
		input "motions", "capability.motionSensor", multiple: true, required: true, title: "Motion sensor(s)"
	}
    section("Choose the thermostat to change when appropriate.") {
        input "thermostat", "device.myEcobeeDevice", required: true, multiple: false, title: "Which thermostat?"
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    subscribe(motions, "motion", motionChangeHandler)
    setCurrentMotions()
}

def updated() {
    unsubscribe()
    subscribe(motions, "motion", motionChangeHandler)
    setCurrentMotions()
}

// Ecobee sensors reset to "inactive" after approximately 30 minutes of no motion detected.
def motionChangeHandler(evt) {
    sendNotificationEvent("Sensor ${evt.device.getLabel()} has changed to: ${evt.value}")
    
    // Rewrite the entire object each time an event occurs since it appears the Ecobee device handler misses motion change events from time to time.
    setCurrentMotions()

    // If the sensor is no longer detecting motion, take certain actions here.
    if (evt.value == "inactive") {
        def parser = new JsonSlurper()
        def tracking_list = parser.parseText(appSettings.notification_motions)
        // We only watch certain sensors in order to try and save energy in certain rooms.
        if (tracking_list.contains(evt.device.getLabel())) {
        	def label = evt.device.getLabel().toString().replace("(Ecobee) ", "")
            // Send a notification, but only between the hours of 8AM and 11PM
            def df = new java.text.SimpleDateFormat("H")
            // Ensure the new date object is set to local time zone
            df.setTimeZone(location.timeZone)
            def hour = df.format(new Date())
            
            if (hour.toInteger() >= 8 && hour.toInteger() <= 23) {
                sendPush("${label} is no longer detecting motion. Make sure the light is turned off.")
            }
        }
        
        // Retrieve the current status of all motion sensors.
        def current_motions = getCurrentMotions()
        
        // If all sensors on the network are no longer tracking motion, take certain actions here.
        if (current_motions.size == 0) {
    		// Check to see whether the Ecobee is in "Home" or "Home and holding" mode.
    		def set_climate = thermostat.currentValue("setClimate").toString()
            if (set_climate == "Home") {
                // Set the thermostat to "Away and holding", which will hold until motion is detected at a sensor or a presence sensor enters the network.
                // Only do this if the current system location setting is not set to "Away", which means we are on vacation and these rules are overridden.
                // NOTE: If we want the holds to expire at the next scheduled activity, make sure the "holdType" preference in the ecobee device settings is set to "nextTransition"
                if (location.currentMode.toString() != "Away") {
                    thermostat.setThisTstatClimate("Away")

                    sendNotificationEvent("All Ecobee motion sensors are idle. Thermostat going into Away mode.")

                    // Send a notification alerting to this change
                    parser = new JsonSlurper()
                    def notification_list = parser.parseText(appSettings.notification_recipients)
                    notification_list.each { phone_number ->
                        sendSms(phone_number, "All motion sensors are idle. Thermostat is going into Away mode.")
                    }
                }
            }
        }
    // If the sensor has detected motion, take certain actions here.
    } else if (evt.value == "active") {
    	// Check to see whether the Ecobee is in "Away and holding" mode.
    	def set_climate = thermostat.currentValue("setClimate").toString()

        // Retrieve the current status of all presence sensors. (Perhaps these apps ran out of order?)
        def current_presence = getCurrentPresence()
        
        if (set_climate == "Away") {
            if (current_presence.size > 0) {
                // If the Ecobee is set to "Away" or "Away and holding" and a presence sensor has been detected, let's just return the Ecobee to normal programming.
                sendNotificationEvent("Motion was detected along with a presence sensor. Thermostat is resuming its normal program.")
                thermostat.resumeProgram()            
            } else {
                // If the Ecobee is set to "Away" or "Away and holding" and only motion is detected, someone is at the house but it's not us. Temporarily set the Ecobee to "Home and holding".
                // Only do this if the current system location setting is not set to "Away", which means we are on vacation and these rules are overridden.
                if (location.currentMode.toString() != "Away") {
                    // Set the thermostat to "Home and holding", which will hold until all sensors are inactive or a presence sensor is detected.
                    // NOTE: If we want the holds to expire at the next scheduled activity, make sure the "holdType" preference in the ecobee device settings is set to "nextTransition"
                    thermostat.setThisTstatClimate("Home")

                    sendNotificationEvent("Motion has been detected by one or more Ecobee motion sensors. Thermostat going into Home mode.")

                    // Send a notification alerting to this change
                    parser = new JsonSlurper()
                    def notification_list = parser.parseText(appSettings.notification_recipients)
                    notification_list.each { phone_number ->
                        sendSms(phone_number, "Motion has been detected at home. Thermostat is going into Home mode.")
                    }
                }
            }
        }
    }
}

def setCurrentMotions() {
    def current_motions = [:]
    def generator = new JsonOutput()
    motions.each { object ->
        def label = object.getLabel()
        def value = object.currentValue("motion")
        current_motions[label] = value
    }

    def current_motions_json = generator.toJson(current_motions)
    sendNotificationEvent("Current motion: ${current_motions_json}")
    // Use atomicState so that the values can be saved to the database and used within the same runtime
    atomicState.current_motions = current_motions_json
}

def getCurrentMotions() {
	def parser = new JsonSlurper()
    def current_motions = parser.parseText(atomicState.current_motions)
    return current_motions
}

def getCurrentPresence() {
	def parser = new JsonSlurper()
    def current_presence = parser.parseText(atomicState.current_presence)
    return current_presence
}