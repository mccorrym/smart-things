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
    section("Which sensor monitors the clothes dryer?") {
	    input "dryer", "capability.powerMeter", required: true, multiple: false, title: "Choose the clothes dryer sensor"
    }
    section("Which lights should change color?") {
    	input "lights", "capability.colorControl", required: true, multiple: true, title: "Choose the color-changing lights"
    }
}

def installed() {
    log.debug dryer.capabilities
    log.debug ("Current switch status: ${lights.currentValue("switch")}")
    log.debug ("Current level: ${lights.currentValue("level")}")
    log.debug ("Current color saturation: ${lights.currentValue("saturation")}")
    log.debug ("Current hue: ${lights.currentValue("hue")}")
    log.debug ("Current color temperature: ${lights.currentValue("colorTemperature")}")
    subscribe(dryer, "power", powerChangeHandler)
}

def updated() {
    unsubscribe()
    subscribe(dryer, "power", powerChangeHandler)
}

def powerChangeHandler (evt) {
   log.debug "Sensor ${evt.device.getLabel()} has changed to: ${evt.value}"
}