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
		input "sensor", "capability.presenceSensor", required: true, multiple: true, title: "Which sensor(s) to monitor?"
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
    subscribe(sensor, "presence", presenceChangeHandler)
    log.debug sensor.getSupportedAttributes()
}

def updated() {
	unsubscribe()
	subscribe(sensor, "presence", presenceChangeHandler)
}

def presenceChangeHandler(evt) {
	log.debug evt.value
}