import groovy.json.JsonOutput

metadata {
  definition(name: "Roomie Activity Switch", namespace: "jc.roomie", author: "Jason Cumberland") {
    capability "Actuator"
    capability "Switch"
    capability "Outlet"
    command "testOn"
    command "testOff"
  }
  preferences {
    input name: "roomieHost", type: "string", title: "Roomie Host/IP (e.g., 192.168.2.196)", required: true
    input name: "uuidOn",     type: "string", title: "Activity UUID for ON", required: true
    input name: "uuidOff",    type: "string", title: "Activity UUID for OFF (System Off)", required: true
    input name: "autoReset",  type: "bool",   title: "Auto-reset switch to OFF after command?", defaultValue: true
    input name: "debugLog",   type: "bool",   title: "Enable debug logging", defaultValue: true
  }
}

def installed() {}
def updated() {}

def on()  { runActivity(uuidOn,  "on")  }
def off() { runActivity(uuidOff, "off") }

def testOn()  { on()  }
def testOff() { off() }

private runActivity(String uuid, String which){
  if (!uuid || !roomieHost) { log.warn "Missing ${uuid ? 'roomieHost' : 'UUID'} for ${which}"; return }
  Map params = [
    uri: "http://${roomieHost}:47147/api/v1/runactivity",
    headers: ["Content-Type":"application/json"],
    body: JsonOutput.toJson([au: uuid])
  ]
  try {
    if (debugLog) log.debug "Roomie ${which.toUpperCase()} → ${params.uri} : ${params.body}"
    httpPost(params) { resp ->
      if (debugLog) log.debug "Roomie resp: ${resp?.status} ${resp?.data}"
      sendEvent(name: "switch", value: (which == "on" ? "on" : "off"), isStateChange: true)
      if (autoReset) runInMillis(400) { sendEvent(name:"switch", value:"off", isStateChange:true) }
    }
  } catch (e) {
    log.warn "Roomie ${which} failed: ${e}"
  }
}
