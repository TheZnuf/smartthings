/**
 *  Copyright 2020 Magnus Solvåg
 */
metadata {
	definition (name: "Heatit Z-TRM2fx", namespace: "ms", author: "Magnus Solvåg", ocfDeviceType: "oic.d.thermostat", cstHandler: true) {
		capability "Actuator"
		capability "Temperature Measurement"
		capability "Thermostat Mode"
		capability "Thermostat Heating Setpoint"
		capability "Thermostat Operating State"
		capability "Energy Meter"
		capability "Power Meter"
		capability "Refresh"
		capability "Sensor"
		capability "Health Check"

		command "switchMode"
		command "lowerHeatingSetpoint"
		command "raiseHeatingSetpoint"
		command "setup"

		fingerprint mfr: "019B", prod: "0003", model: "0202", deviceJoinName: "HeatIt Z-TRM2fx"
	}

	tiles(scale:2) {
		multiAttributeTile(name:"main", type:"thermostat", width:6, height:4) {
			tileAttribute("device.heatingSetpoint", key: "VALUE_CONTROL") {
				attributeState("VALUE_UP", action: "raiseHeatingSetpoint")
				attributeState("VALUE_DOWN", action: "lowerHeatingSetpoint")
			}
			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
				attributeState("temp", label:'${currentValue}°', defaultState: true)
			}
			tileAttribute("device.thermostatOperatingState", key: "OPERATING_STATE") {
				attributeState("idle", backgroundColor:"#00A0DC", label:'${currentValue}')
				attributeState("heating", backgroundColor:"#e86d13", label:'${currentValue}')
			}
		}
		valueTile("operatingState", "device.thermostatOperatingState", width: 2, height: 1, inactiveLabel: false, decoration: "flat") {
			state "heating", label:'On', backgroundColor: "#e86d13"
			state "idle", label:'Off', backgroundColor: "#00A0DC", defaultState: true
		}
		standardTile("mode", "device.thermostatMode", width:2, height:1, inactiveLabel: false, decoration: "flat") {
			state "heat", action:"switchMode", nextState:"...", label: "Comfort"
			state "eco", action:"switchMode", nextState:"...", label: "Eco"
			state "off", action:"switchMode", nextState:"...", label: "Off"
			state "...", label: "...", nextState:"..."
		}
		controlTile("temperatureControl", "device.heatingSetpoint", "slider", height: 1, width: 2, inactiveLabel: false, range:"(10..40)") {
			state "level", action:"setHeatingSetpoint"
		}
		valueTile("power", "device.power", width: 3, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'${currentValue} W'
		}
		valueTile("energy", "device.energy", width: 3, height: 1, inactiveLabel: false, decoration: "flat") {
			state "default", label:'${currentValue} kWh'
		}
	   	standardTile("refresh", "command.refresh", width:2, height:1, inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh", icon:"st.secondary.refresh"
		}
		standardTile("setup", "command.setup", width:2, height:1, inactiveLabel: false, decoration: "flat") {
			state "default", action:"setup", label:"reset"
		}
		main "main"
		details(["main", "operatingState", "mode", "temperature", "temperatureControl", "power", "energy", "refresh", "setup"])
	}
}

private getCommandClassCapabilities() {[
	0x85:1, // Association
	0x59:1, // Association Group Info
	0x8E:2, // Multi Channel Association (supports v3)
	0x86:1, // Version (suppports v3)
	0x70:2, // Configuration (supports v3)
	0x72:2, // Manufacturer Specific
	0x60:3, // Multi Channel (supports v4)
	0x20:1, // Basic (supports v2)
	0x31:5, // Sensor Multilevel
	0x43:2, // Thermostat Setpoint (supports v3)
	0x40:2, // Thermostat Mode (supports v3)
	0x25:1, // Switch Binary
	0x32:3, // Meter
	0x98:1, // Security
	0x80:1, // Battery
]}

private getSupportedModes(){[
	"off": 0,
	"heat": 1,
	"eco": 11
]}
private getSupportedModeNames(){
	supportedModes.collect{entry -> entry.key}
}

private getMeterMetrics(){[
	(0): [name: "energy", unit: "kWh"],
	(1): [name: "energykVAh", unit: "kVAh"],
	(2): [name: "power", unit: "W"],
	(4): [name: "voltage", unit: "V"],
	(5): [name: "amperage", unit: "A"]
]}

private getOperatingStates(){[
	(physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE) : "idle",
	(physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING): "heating",
	(physicalgraph.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_PENDING_HEAT): "pending"
]}

private getOperatingModes(){[
	(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_OFF): "off",
	(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_HEAT): "heat",
	(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport.MODE_ENERGY_SAVE_HEAT): "eco"
]}

private getConstants(){[
	"checkInterval": 32 * 60,
	"heatingModes": [1, 11],
	"scale": [(0): "C", (1): "F"],
	"metrics": ["energy": 0, "power": 2],
	"channels": ["floorSensor": 3, "switch": 4]
]}

private getConfigParams(){[
	(19): [name: "tempReportInterval", size: 2, default: [0,0], unit: "seconds"],
	(21): [name: "meterReportInterval", size: 2, default: [0,0], unit: "seconds"]
]}

private getCurrentMode(){ device.currentValue("thermostatMode") }
private getCurrentTemp(){ device.currentValue("heatingSetpoint") }
private getCurrentSetpoint(){ device.currentState("heatingSetpoint") }

def installed() {
	log.info "installed"
	runIn(1, "setup", [overwrite: true])
}

def updated() {
	log.info "updated"
	runIn(1, "setup", [overwrite: true])
}

def setup() {
	log.info "setup"

	sendEvent(name: "checkInterval", value: constants.checkInterval,
		displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	sendEvent(name: "supportedThermostatModes", value: supportedModeNames)
	
	unschedule()
	runEvery5Minutes("query")
	runIn(1, "query", [overwrite: true])
	
	sendHubCommand([
		configParams.collect{param -> zwave.configurationV2.configurationSet(
			parameterNumber: param.key, size: param.value.size, configurationValue: param.value.default)},
		configParams.collect{param -> zwave.configurationV2.configurationGet(parameterNumber: param.key)}
	].flatten())
}

def query() {
	log.info "query"
	def type = supportedModes[currentMode] ?: supportedModes.heat

	sendHubCommand([
		zwave.thermostatModeV2.thermostatModeGet(),
		zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: type),
		toChannel(constants.channels.floorSensor, zwave.sensorMultilevelV5.sensorMultilevelGet()),
		toChannel(constants.channels.switch, zwave.switchBinaryV1.switchBinaryGet())
	])
}

def queryMetrics() {
	log.info "queryMetrics"
	sendHubCommand([
		zwave.meterV3.meterGet(scale: constants.metrics.energy),
		zwave.meterV3.meterGet(scale: constants.metrics.power)
	])
}

def parse(String description) {
	def cmd = zwave.parse(description)
	if(!cmd) return null
	
	log.debug cmd
	def events = zwaveEvent(cmd)
	if(!events)
		log.warn "No events for command: ${cmd}"
	return events
}

def zwaveEvent(physicalgraph.zwave.Command cmd, ep = null) {
	log.warn "Unhandled event, cmd: ${cmd}, ep: ${ep}"
	return null
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	log.warn "SecurityMessageEncapsulation $cmd"
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassCapabilities)
	if (!encapsulatedCommand) return null
	
	state.sec = 1
	log.debug encapsulatedCommand
	return zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {    
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassCapabilities)
	if (!encapsulatedCommand) return null

	log.debug encapsulatedCommand
	return zwaveEvent(encapsulatedCommand, cmd.sourceEndPoint as Integer)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd, ep = null) {
	if(!constants.heatingModes.contains(cmd.setpointType as Integer)) return null
	if(!cmd.scaledValue) return null

	return createEvent(
		name: "heatingSetpoint",
		value: toClientTemperature(cmd.scaledValue, constants.scale[cmd.scale as Integer]),
		unit: getTemperatureScale(), 
	   	displayed: false
	)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd, ep = null) {
	if(cmd.sensorType != 1) return null
	if(!cmd.scaledSensorValue) return null

	return createEvent(
		name: "temperature",
		value: toClientTemperature(cmd.scaledSensorValue, constants.scale[cmd.scale as Integer]),
		unit: getTemperatureScale()
	)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport cmd, ep = null) {
	def mode = operatingModes[cmd.mode]
	if(!mode) return null

	return createEvent([
		name: "thermostatMode", 
		value: mode,
		data: [supportedThermostatModes: supportedModeNames]
	])
}

def zwaveEvent(physicalgraph.zwave.commands.meterv3.MeterReport cmd, ep = null) {
	def metric = meterMetrics[cmd.scale as Integer]
	if(!metric) return null
	
	def roundedValue = (float) (Math.round(cmd.scaledMeterValue * 10.0) / 10.0)
	return createEvent(metric + [value: roundedValue])
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd, ep = null) {
	def param = configParams[cmd.parameterNumber as Integer]
	if(!param) return null
	
	def desc = "config param ${cmd.parameterNumber}:${param.name} = ${cmd.scaledConfigurationValue} ${param.unit}"
	log.info desc
	return createEvent(descriptionText: desc, displayed: true)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd, ep = null) {
	return parseOperatingState(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd, ep = null) {
 	if (!ep) return null
	return parseOperatingState(cmd)
}

private parseOperatingState(cmd) {
	def map = [name: "thermostatOperatingState", value: "idle"]
	def mode = supportedModes[currentMode] ?: supportedModes.heat
	if(cmd.value && constants.heatingModes.contains(mode))
		map.value = "heating"

	runIn(4, "queryMetrics", [overwrite:true])	
	return createEvent(map)
}

// Command Implementations
def refresh() {
	log.info "refresh"
	runIn(3, "query", [overwrite: true])
}

def ping() {
	log.info "ping"
	sendHubCommand(toChannel(
		constants.channels.switch, 
		zwave.switchBinaryV1.switchBinaryGet()
	))
}

def raiseHeatingSetpoint() {
	setHeatingSetpoint(currentTemperature + 1)
}

def lowerHeatingSetpoint() {
	setHeatingSetpoint(currentTemperature - 1)
}

def setHeatingSetpoint(setpoint) {
	log.info "setHeatingSetpoint(setpoint: $setpoint)"
	if(!currentSetpoint || setpoint <= 1) return
   
	def minSetpoint = toClientTemperature(5, "C")
	def maxSetpoint = toClientTemperature(40, "C")
	def limitedValue = toClientTemperature(setpoint, "C")
	limitedValue = limitedValue > maxSetpoint 
		? maxSetpoint 
		: (limitedValue > minSetpoint ? limitedValue : minSetpoint)

	sendEvent(
		"name": "heatingSetpoint",
		"value": limitedValue,
		unit: getTemperatureScale(), 
		eventType: "ENTITY_UPDATE", 
		displayed: false
	)

	def type = supportedModes[currentMode] ?: supportedModes.heat
	sendHubCommand(
		zwave.thermostatSetpointV2.thermostatSetpointSet(
			setpointType: type, 
			scaledValue: limitedValue,
			scale: currentSetpoint.unit
	))
	
	refresh()
}

def switchMode() {
	log.info "switchMode"
   	def modes = supportedModes.collect{entry -> entry.key}.drop(1)
	def next = {modes[modes.indexOf(it) + 1] ?: modes[0]}
	def nextMode = next(currentMode)

	setThermostatMode(nextMode)
}

def setThermostatMode(mode) {
	log.info "setThermostatMode(mode: $mode)"

	if(!supportedModes.containsKey(mode)){
		log.warn "Unknown termostat mode: $mode"
		return []
	}

	def setpointType = supportedModes[mode]
	sendHubCommand([
		zwave.thermostatModeV2.thermostatModeSet(mode: setpointType)
	])
	
	refresh()
}

def auto(){  }
def cool(){ }
def emergencyHeat(){ }
def heat(){ setThermostatMode('heat') }
def off(){ setThermostatMode('off') }

// Utils
private getCurrentTemperature() {
	def temp = getCurrentSetpoint()
	if (!temp || !temp.value || !temp.unit) return 0
	return toClientTemperature(temp.value.toBigDecimal(), temp.unit)
}

private toClientTemperature(temp, scale) {
	if (!temp || !scale) return 0
	
	def scaledTemp = convertTemperatureIfNeeded(
		temp.toBigDecimal(), scale
	).toDouble()

	return getTemperatureScale() == "F" 
		? scaledTemp.round(0).toInteger() 
		: (Math.round(scaledTemp * 2)) / 2
}

private toChannel(channel, cmd, bitMask = false) {
	return zwave.multiChannelV3.multiChannelCmdEncap(
		bitAddress: bitMask, 
		destinationEndPoint: channel
	).encapsulate(cmd)
}
