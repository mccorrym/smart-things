/**
 *  Color Coded Alerts
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
 
definition(
	name: "Color Coded Alerts",
	namespace: "smartthings",
	author: "Matt",
	description: "Monitor selected sensors and switches and notify of any changes or alerts using specific color changes in Philips Hue bulbs.",
	category: "Convenience",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Developers/smart-light-timer.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Developers/smart-light-timer@2x.png"
)

preferences {
	section("Which sensors monitor the washer and dryer?") {
		input "laundry_devices", "capability.powerMeter", required: true, multiple: false, title: "Choose the washer and dryer sensors"
	}
	section("Which lights should change color?") {
		input "lights", "capability.colorControl", required: true, multiple: true, title: "Choose the color changing lights"
	}
}

def installed() {
	log.debug laundry_devices.capabilities
	log.debug ("Current switch status: ${lights.currentValue("switch")}")
	log.debug ("Current level: ${lights.currentValue("level")}")
	log.debug ("Current color saturation: ${lights.currentValue("saturation")}")
	log.debug ("Current hue: ${lights.currentValue("hue")}")
	log.debug ("Current color temperature: ${lights.currentValue("colorTemperature")}")
 	
	state.laundry_devices = [:]
	state.laundry_devices["dryer"] = ["running": false, "light_sequence": "dryerLightSequence"]
           
	subscribe(laundry_devices, "power", powerChangeHandler)
}

def updated() {
	unsubscribe()
	state.laundry_devices = [:]
	state.laundry_devices["dryer"] = ["running": false, "light_sequence": "dryerLightSequence"]
    
	subscribe(laundry_devices, "power", powerChangeHandler)
}

def powerChangeHandler (evt) {
	log.debug "Sensor ${evt.device.getLabel()} has changed to: ${evt.value}"
	if (evt.value.toFloat() > 1) {
    	if (state.laundry_devices[evt.device.getLabel().toLowerCase()]["running"] == false) {
			sendNotificationEvent("[EVENT] ALERT: The ${evt.device.getLabel().toLowerCase()} has started.")
		}
        state.laundry_devices[evt.device.getLabel().toLowerCase()]["running"] = true
	} else {
		if (state.laundry_devices[evt.device.getLabel().toLowerCase()]["running"] == true) {
			sendNotificationEvent("[EVENT] ALERT: The ${evt.device.getLabel().toLowerCase()} has finished.")
			sendPush("The ${evt.device.getLabel().toLowerCase()} has finished!")
            def lights_on = false
            lights.any { object ->
            	if (object.currentValue("switch") == "on") {
                	lights_on = true
                    return true
                }
            }
			if (lights_on) {
				runIn(5, state.laundry_devices[evt.device.getLabel().toLowerCase()]["light_sequence"], [overwrite: false])
				runIn(8, backToNormal, [overwrite: false])
			}
			state.laundry_devices[evt.device.getLabel().toLowerCase()]["running"] = false
		}
	}
}

// The color code for the dryer finishing is GREEN
def dryerLightSequence() {
	def newValue = [hue: 37, saturation: 100, level: 100, temperature: 6500]
	lights.setColor(newValue)
}

// Return the lights to the default hue/temperature
def backToNormal() {
	def newValue = [hue: 13, saturation: 55, level: 100, temperature: 2732]
	lights.setColor(newValue)
}