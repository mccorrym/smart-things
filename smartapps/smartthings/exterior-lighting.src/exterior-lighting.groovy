definition(
    name: "Exterior Lighting",
    namespace: "smartthings",
    author: "Matt",
    description: "Monitor lighting to determine when to turn on or off exterior lighting.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/App-LightUpMyWorld@2x.png"
)

preferences {
	section("Choose the light sensor and switches you'd like to control.") {
		input "sensor", "capability.illuminanceMeasurement", required: true, title: "Which sensor to monitor?"
    	input "switches", "capability.switch", required: true, multiple: true, title: "Which switch(es) to control?"
    	input "holiday_switches", "capability.switch", required: false, multiple: true, title: "Choose any holiday lights (optional)"
    	input "holiday_time", "time", required: false, title: "Choose a time to turn the holiday lights off"
	}
}

def installed() {
    subscribe(sensor, "illuminance", illuminanceChangeHandler)
    subscribe(switches, "switch", switchChangeHandler)
    unschedule(holidayLightsHandler)
    if (holiday_switches != null) {
    	schedule(holiday_time, holidayLightsHandler)
    }
}

def updated() {
    unsubscribe()
    subscribe(sensor, "illuminance", illuminanceChangeHandler)
    subscribe(switches, "switch", switchChangeHandler)
    unschedule(holidayLightsHandler)
    if (holiday_switches != null) {
    	schedule(holiday_time, holidayLightsHandler)
    }
}

def switchChangeHandler (evt) {
    state.switch_value = evt.value
}

def illuminanceChangeHandler (evt) {
    def lux_measurement = evt.integerValue
    
    log.trace("Switch status: ${switch_status}")
    
    if (state.lux_on_date != null && lux_measurement >= 200) {
    	// False alarm. Keep the lights OFF and reset the 3 minute timer.
        log.trace ("False alarm - lux value is now ${lux_measurement}. Keeping lights off.")
        state.lux_on_date = null
    } else if (state.lux_off_date != null && lux_measurement <= 200) {
    	// False alarm. Keep the lights ON and reset the 3 minute timer.
        log.trace ("False alarm - lux value is now ${lux_measurement}. Keeping lights on.")
        state.lux_off_date = null
    } else {
    	log.trace ("Lux value is ${lux_measurement}.")
    }
    
    if (lux_measurement < 200) {
        // Lights are off and lux measurement is < 200. Should we turn the lights on?
        if (state.lux_on_date != null) {
            def current_date = new Date().getTime() / 1000
            // Check to make sure at least 3 minutes have passed (to avoid fluke values from turning the lights on/off in succession)
            if ((current_date - state.lux_on_date) >= 30) {
                // Reset the lux_on_date to NULL to prepare for the next event
                state.lux_on_date = null

                // Check the status of the switches in the array. If all switches are already ON, exit the routine.
                def switches_on = true
                switches.any { object ->
                    if (object.currentSwitch == "off") {
                    	switches_on = false
                        return true
                    }
                }
                if (switches_on) {
                    return
                }
                
                def df = new java.text.SimpleDateFormat("H")
                // Ensure the new date object is set to local time zone
                df.setTimeZone(location.timeZone)
                def hour = df.format(new Date())

                log.trace("Turning lights ON!")
                switches.each { object ->
                    object.on()
                }

                // If any holiday switches are included in the scene, turn them on now as well
                if (holiday_switches != null) {
                    holiday_switches.each { object ->
                        object.on()
                    }
                }

                if (hour.toInteger() > 15) {
                    sendPush("Good evening! Exterior lights are turning ON.")
                } else {
                    sendPush("Exterior lights are turning ON due to darkness.")
                }
            }
        } else {
            state.lux_on_date = new Date().getTime() / 1000
            log.trace ("Target lux value has been met. Setting 3 minute timer (${state.lux_on_date})")
        }
    }
    if (lux_measurement > 200) {
        // Lights are on and lux measurement is > 200. Should we turn the lights off?
        if (state.lux_off_date != null) {
            def current_date = new Date().getTime() / 1000
            // Check to make sure at least 3 minutes have passed (to avoid fluke values from turning the lights on/off in succession)
            if ((current_date - state.lux_off_date) >= 30) {
                // Reset the lux_off_date to NULL to prepare for the next event
                state.lux_off_date = null

                // Check the status of the switches in the array. If all switches are already OFF, exit the routine.
                def switches_off = true
                switches.any { object ->
                    if (object.currentSwitch == "on") {
                    	switches_off = false
                        return true
                    }
                }
                if (switches_off) {
                    return
                }
                
                def df = new java.text.SimpleDateFormat("H")
                // Ensure the new date object is set to local time zone
                df.setTimeZone(location.timeZone)
                def hour = df.format(new Date())

                log.trace("Turning lights OFF!")
                switches.each { object ->
                    object.off()
                }

                if (hour.toInteger() > 15) {
                    sendPush("Exterior lights are turning back OFF.")
                } else {
                    sendPush("Good morning! Exterior lights are turning OFF.")
                }
            }
        } else {
            state.lux_off_date = new Date().getTime() / 1000
            log.trace ("Target lux value has been met. Setting 3 minute timer (${state.lux_off_date})")
        }
    }
}

// This event is run whenever holiday lights are chosen for the scene. It will turn the holiday switches off at the time specified (holiday_time)
def holidayLightsHandler (evt) {
    log.trace ("Time ${holiday_time} has been reached. Turning the holiday switches OFF.")
	if (holiday_switches != null) {
        holiday_switches.each { object ->
            log.debug(object.currentSwitch)
            object.off()
        }
    }
}