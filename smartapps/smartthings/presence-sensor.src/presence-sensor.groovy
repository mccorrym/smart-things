/**
 *  Presence Sensor
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
    name: "Presence Sensor",
    namespace: "smartthings",
    author: "Matt",
    description: "Monitors the presence sensors on the network and makes changes and/or sends notifications based on their status.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Family/App-PhoneMinder.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Family/App-PhoneMinder@2x.png") {
    appSetting "notification_sensors"
    appSetting "notification_recipients"
}

preferences {
	section("Choose the presence sensor(s) you'd like to monitor.") {
		input "sensors", "capability.presenceSensor", required: true, multiple: true, title: "Which sensor(s) to monitor?"
	}
    section("Choose the thermostat to change when appropriate.") {
        input "thermostat", "device.myEcobeeDevice", required: true, multiple: false, title: "Which thermostat?"
    }
}

mappings {
    path("/current_presence") {
        action: [
            GET: "getCurrentPresenceViaOAuth"
        ]
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    subscribe(sensors, "presence", presenceChangeHandler)
    setCurrentPresence()
}

def updated() {
    unsubscribe()
    subscribe(sensors, "presence", presenceChangeHandler)
    setCurrentPresence()
}

def presenceChangeHandler(evt) {
     // Get the current presence for all sensors
    def current_presence = getCurrentPresence(false)
    sendNotificationEvent("Presence sensor ${evt.device.getLabel()} has changed to: ${evt.value}")
    
    // Only perform an action if the stored presence state does not match the event's presence state for this sensor
    if (current_presence[evt.device.getLabel()] != evt.value) {
        def parser = new JsonSlurper()
        def tracking_list = parser.parseText(appSettings.notification_sensors)
        if (tracking_list.contains(evt.device.getLabel())) {
            // This device is being tracked. Send a notification and update the stored presence setting
            def notification_list = parser.parseText(appSettings.notification_recipients)
            switch(evt.value) {
                case "present":
                    notification_list.each { phone_number ->
                    	sendSms(phone_number, "${evt.device.getLabel()} has arrived home.")
                    }
                    break
                case "not present":
                    notification_list.each { phone_number ->
                    	sendSms(phone_number, "${evt.device.getLabel()} has left home.")
                    }
                    break
            }
            // Update the stored presence setting for this sensor
            triggerPresenceChangeAction(evt)
        } else {
            // Update the stored presence setting for this sensor
            triggerPresenceChangeAction(evt)
        }
    }
}

def triggerPresenceChangeAction(evt) {
    // Update the current presence for all sensors
    setCurrentPresence()
    
    // Get all currently present sensors
    def current_presence = getCurrentPresence(true)
    
    if (current_presence.size() == 0) {
    	// All sensors have left the network. Perform any desired actions here.
    	sendNotificationEvent("All sensors have left the network.")
        // Set the thermostat to "Away and holding" which will hold until the next scheduled activity.
        // NOTE: Make sure the "holdType" preference in the ecobee device settings is set to "nextTransition"
        // Only do this if the current system location setting is not set to "Away", which means we are on vacation and these rules are overridden.
        if (location.currentMode.toString() != "Away") {
            sendNotificationEvent("Setting the thermostat to Away mode.")
            thermostat.setThisTstatClimate("Away")
            
            // Send a notification alerting to this change
            def parser = new JsonSlurper()
            def notification_list = parser.parseText(appSettings.notification_recipients)
            notification_list.each { phone_number ->
                sendSms(phone_number, "All sensors have left the network. Setting the thermostat to Away mode.")
            }
        }
    } else {
    	// At least one sensor is in the network. Perform any desired actions here.
    	// Check to see whether the Ecobee is in "Away and holding" or "Home and holding" mode.
        def program_type = thermostat.currentValue("programType").toString()
        if (program_type == "hold") {
            // The thermostat is in "Away and holding" or "Home and holding" mode. Resume its normal programming.
            sendNotificationEvent("A sensor has entered the network. Thermostat is resuming its normal program.")
            thermostat.resumeProgram()
        }
    }
}

def setCurrentPresence() {
    def current_presence = [:]
    def generator = new JsonOutput()
    sensors.each { object ->
        def label = object.getLabel()
        def value = object.currentValue("presence")
        current_presence[label] = value
    }
    
    def current_presence_json = generator.toJson(current_presence)
    sendNotificationEvent("Setting current presence: ${current_presence_json}")
    // Use atomicState so that the values can be saved to the database and used within the same runtime
    atomicState.current_presence = current_presence_json
}

def getCurrentPresence(present_only=false) {
	def parser = new JsonSlurper()
    def current_presence = parser.parseText(atomicState.current_presence)
    // If the present_only flag passed in is TRUE, only return the sensors in the object that are currently present
    if (present_only == true) {
        def presence_only = [:]
        current_presence.each { label, value ->
            if (value == "present") {
                presence_only[label] = value
            }
        }
        return presence_only
    }
    // Otherwise, return the full object of all sensors and their current status
    return current_presence
}

def getCurrentPresenceViaOAuth() {
	// Send the response using text/plain as opposed to the default (text/json) since it appears this version of Groovy does not handle httpGet() calls with JSON responses well
    render contentType: "text/plain", data: atomicState.current_presence, status: 200
}