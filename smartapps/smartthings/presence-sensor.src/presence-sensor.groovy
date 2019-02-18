/**
 *  Presence Sensor
 *
 *  Copyright 2018 Matt
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

def installed() {
    log.debug "Installed with settings: ${settings}"
    subscribe(sensors, "presence", presenceChangeHandler)
    def presence = [:]
    sensors.each { object ->
        def label = object.getLabel()
        def value = object.currentValue("presence")
        log.debug "Setting ${label} to presence setting ${value}"
        presence[label] = value
    }
    state.presence = presence
}

def updated() {
    unsubscribe()
    subscribe(sensors, "presence", presenceChangeHandler)
    def presence = [:]
    sensors.each { object ->
        def label = object.getLabel()
        def value = object.currentValue("presence")
        log.debug "Setting ${label} to presence setting ${value}"
        presence[label] = value
    }
    state.presence = presence
}

def presenceChangeHandler(evt) {
    log.debug "evt.value ${evt.value}"
    log.debug "evt.device ${evt.device.getLabel()}"
    
    // Only perform an action if the stored presence state does not match the event's presence state for this sensor
    if (state.presence[evt.device.getLabel()] != evt.value) {
        def parser = new JsonSlurper()
        def tracking_list = parser.parseText(appSettings.notification_sensors)
        if (tracking_list.contains(evt.device.getLabel())) {
            // This device is being tracked. Send a notification and update the stored presence setting
            def notification_list = parser.parseText(appSettings.notification_recipients)
            switch(evt.value) {
                case "present":
                    log.debug "${evt.device.getLabel()} has arrived home."
                    notification_list.each { phone_number ->
                    	sendSms(phone_number, "${evt.device.getLabel()} has arrived home.")
                    }
                    break
                case "not present":
                    log.debug "${evt.device.getLabel()} has left home."
                    notification_list.each { phone_number ->
                    	sendSms(phone_number, "${evt.device.getLabel()} has left home.")
                    }
                    break
            }
            // Update the stored presence setting for this sensor
            updatePresence(evt)
        } else {
            // Update the stored presence setting for this sensor
            updatePresence(evt)
        }
    }
}

def updatePresence(evt) {
    state.presence[evt.device.getLabel()] = evt.value
    def presence = []
    state.presence.each { sensor_name, sensor_presence ->
    	if (sensor_presence != "not present") {
        	presence.push(sensor_name)
        }
    }
    if (presence.size == 0) {
    	// All sensors have left the network. Perform any desired actions here.
    	sendNotificationEvent("All sensors have left the network.")
        // Set the thermostat to "Away and holding" which will hold until the next scheduled activity.
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
    }
}