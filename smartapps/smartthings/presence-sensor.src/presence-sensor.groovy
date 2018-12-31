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
definition(
    name: "Presence Sensor",
    namespace: "smartthings",
    author: "Matt",
    description: "Manages the notifications that are sent for the presence sensors on the network.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/text.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/text@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Meta/text@2x.png") {
    appSetting "sensor_name"
    appSetting "notification_list"
}

preferences {
	section("Choose the presence sensor(s) you'd like to monitor.") {
		input "sensors", "capability.presenceSensor", required: true, multiple: true, title: "Which sensor(s) to monitor?"
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
        presence << [label:value]
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
        presence << [label:value]
    }
    state.presence = presence
}

def presenceChangeHandler(evt) {
    log.debug "evt.value ${evt.value}"
    log.debug "evt.device ${evt.device.getLabel()}"
    // Testing
    if (evt.device.getLabel() == "sensor[0]") {
    	log.debug "Sensor 0 was tripped. Current sensor 0 value: ${state.presence["sensor[0]"]}"
    } else {
    	log.debug "A different sensor was tripped"
    }
}