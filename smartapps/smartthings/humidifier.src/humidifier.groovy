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

import groovy.time.TimeCategory 
import groovy.time.TimeDuration

definition(
    name: "Humidifier",
    namespace: "smartthings",
    author: "Matt",
    description: "Monitors thermostat actions, indoor humidity and outdoor temperature and turns a switch on to the humidifier in the event humidity needs raising.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png") {
    appSetting "notification_recipients"
}

preferences {
    section("Choose the thermostat to read values from and the humidifier switch to control.") {
        input "thermostat", "device.myEcobeeDevice", required: true, multiple: false, title: "Select the thermostat."
        input "humidifier", "capability.switch", required: true, multiple: false, title: "Select the humidifier switch."
        input "humidifer_runtime", "enum", required: true, title: "Select how long to run the humidifier (in minutes) during each call for heat.", options: [1, 2, 3, 4, 5]
    }
}

def installed() {
    subscribe(thermostat, "thermostatOperatingState", thermostatOperatingHandler)
    // A fail safe to prevent the humidifier from being left running
    runEvery10Minutes("humidifierSwitchHandler")
    // Reset the humidifier run time each day
    def midnight = new Date()
    midnight.set(hourOfDay: 0, minute: 0, second: 0)
    schedule(midnight, "humidifierRunTimeReset")
    humidifierRunTimeReset()
}

def updated() {
    unsubscribe()
    unschedule()
    subscribe(thermostat, "thermostatOperatingState", thermostatOperatingHandler)
    
    // A fail safe to prevent the humidifier from being left running
    runEvery10Minutes("humidifierSwitchHandler")
    // Reset the humidifier run time each day
    def midnight = new Date()
    midnight.set(hourOfDay: 0, minute: 0, second: 0)
    schedule(midnight, "humidifierRunTimeReset")
    humidifierRunTimeReset()
}

def thermostatOperatingHandler(evt) {
	// A map of outdoor temperature : recommended indoor relative humidity
    def humidity_map = [:]
    humidity_map = [
        "40" : "45",
        "30" : "40",
        "20" : "35",
        "10" : "30",
        "0" : "25",
        "-10" : "20",
        "-20" : "15"
    ]
	if (evt.value.toString() == "heating") {
    	def current_temperature = thermostat.currentValue("weatherTemperatureDisplay").toInteger()
        def current_humidity = thermostat.currentValue("remoteSensorAvgHumidity").toInteger()
        
        sendNotificationEvent("[HUMIDIFIER] The furnace has called for heat. Outdoor temperature: ${current_temperature}. Indoor humidity: ${current_humidity}%.")
        
        def target_humidity = null
        humidity_map.any { temperature, humidity ->
        	if (current_temperature <= temperature.toInteger()) {
            	target_humidity = humidity.toInteger()
            } else {
            	return true
            }
        }
        
        if (current_humidity <= target_humidity) {
        	if (humidifier.currentValue("switch") == "off") {
            	// Log the humidifier run time
                state.humidifier_on = new Date()
            	// Turn the humidifer switch ON
                humidifer.on()
                // Run it for the selected time period
                runIn((humidifer_runtime.toInteger() * 60), humidifierSwitchHandler)
                
                sendNotificationEvent("[HUMIDIFIER] turning ON.")
            }
        } else {
        	sendNotificationEvent("[HUMIDIFIER] staying off. The current humidity of ${current_humidity} exceeds the target of ${target_humidity} at outdoor temperature ${current_temperature}.")
        }
    } else if (evt.value.toString() == "idle") {
    	// If the humidifier is still running, turn it off
        humidifier.off()
    }
}

def humidifierSwitchHandler() {
	// Log the humidifier run time
	def diff = groovy.time.TimeCategory.minus(new Date(), state.humidifier_on)
    state.humidifier_runtime = state.humidifier_runtime + duration.minutes
    
    sendNotificationEvent("[HUMIDIFIER] turning OFF. Total runtime today: ${state.humidifier_runtime} minutes.")
    
	// Turn the humidifier switch OFF
    humidifier.off()
}

def humidifierRunTimeReset() {
	// Reset the humidifier run time
    state.humidifier_runtime = 0
}