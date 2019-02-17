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

definition(
    name: "Motion Efficiency",
    namespace: "smartthings",
    author: "Matt",
    description: "Monitor Ecobee remote motion sensors to increase energy efficiency.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Allstate/motion_detected.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Allstate/motion_detected@2x.png") {
    appSetting "notification_motions"
}

preferences {
	section("Choose the motion sensors you'd like to monitor.") {
		input "motions", "capability.motionSensor", title: "Motion sensor(s)", multiple: true, required: true
	}
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    subscribe(motions, "motion", motionChangeHandler)
}

def updated() {
    unsubscribe()
    subscribe(motions, "motion", motionChangeHandler)
}

// Ecobee sensors reset to "inactive" after approximately 30 minutes of no motion detected.
def motionChangeHandler(evt) {
    // If the sensor is no longer detecting motion, take certain actions.
    if (evt.value == "inactive") {
        def parser = new JsonSlurper()
        def tracking_list = parser.parseText(appSettings.notification_motions)
        // We only watch certain sensors in order to try and save energy in certain rooms.
        if (tracking_list.contains(evt.device.getLabel())) {
        	def label = evt.device.getLabel().toString().replace("(Ecobee) ", "")
            // Send a notification
            sendPush("${label} is no longer detecting motion. Make sure the light is turned off.")
        }
    }
}