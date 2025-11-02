definition(
  name: "Roomie Activity Importer",
  namespace: "jc.roomie",
  author: "Jason Cumberland",
  description: "Bulk import Roomie activities as Alexa-controllable virtual switches.",
  category: "Convenience",
  singleInstance: true,
  iconUrl: "https://raw.githubusercontent.com/myrison/hubitat-roomie-integration/main/icons/roomie_logo.png",
  iconX2Url: "https://raw.githubusercontent.com/myrison/hubitat-roomie-integration/main/icons/roomie_logo_2x.png",
)

private String h2(String txt) {
  return "<div style='font-size:20px;font-weight:700;margin:12px 0 6px;'>${txt}</div>"
}

preferences { page(name: "mainPage") }

def mainPage() {
  dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {

    // Connection (H2)
    section("") {
      paragraph h2("Connection")
      input "roomieHost", "string",
        title: "Roomie Host/IP (e.g., 192.168.2.196)",
        required: true, defaultValue: "192.168.2.196",
        submitOnChange: true
    }

    // CSV Input (H2)
    section("") {
      paragraph h2("CSV Input")
      paragraph """<b>Format (header optional)</b><br>
      Expected columns: <code>Name,OnUUID,OffUUID</code>.<br>
      Examples:
      <code>
      Theater Plex,6C4FB652-AFE2-4F3F-8AB8-07CB4F7235E6,F1B1871F-B2B8-4245-A785-1F5C78272D16<br>
      Living Room Apple TV,9D5C8C0D-5ACF-416D-917E-E7DDFBE1DA78,9457BFA2-79EA-4DBE-87C5-9189224386A4
      </code><br>
      Lines starting with <code>#</code> are ignored."""
      input "csvText", "textarea",
        title: "CSV Data",
        required: true, rows: 18,
        submitOnChange: true
    }

    // Options (default styling)
    section("Options") {
      input "autoResetDefault", "bool", title: "Auto-reset switches after commands? Useful in certain scenarios, not required.", defaultValue: false, submitOnChange: true
      input "debugDefault", "bool",     title: "Enable driver debug logging?",      defaultValue: true, submitOnChange: true
      input "useLightCapability", "bool", title: "Advertise as Light instead of Outlet? This allows Alexa to turn on/off your media device when you ask to turn all lights on or off", defaultValue: false, submitOnChange: true
    }

    // Actions (H2)
    section("") {
      paragraph h2("Actions")

      paragraph "<b>Note:</b> If you only wish to change the IP address of your Roomie controller, use this function."
      input name: "applyHostAll", type: "button", title: "Apply Host/IP to All Devices"

      paragraph "<b>Note:</b> If you just edited the CSV section, sometimes you have to click the button 2x. If nothing happens, click the button again until the list of imported or updated devices is shown."
      input name: "doImport", type: "button", title: "Import / Update Devices"
    }

    // Last result (H2 with conditional blue/bold)
    section("") {
      paragraph h2("Last result")
      def defaultMsg = "No import run yet. Tip: after editing the CSV, click the button once to save, then click again to run."
      def msg = state.lastReport ?: defaultMsg
      def styledMsg = (state.lastReport)
        ? "<div style='color:#0066cc;font-weight:700;'>${msg}</div>"
        : "<div>${msg}</div>"
      paragraph styledMsg
    }

    // Child devices (default styling)
    section("Child devices") {
      paragraph childSummary()
    }
  }
}
def appButtonHandler(btn) {
  if (btn == "doImport") {
    if (state._importing) return
    state._importing = true
    try {
      state.lastReport = runImport()
    } finally { state._importing = false }
  } else if (btn == "applyHostAll") {
    def count = updateAllChildrenHost(roomieHost ?: "")
    state.lastReport = "Applied Host/IP '${roomieHost}' to ${count} devices."
  }
}

private String childSummary() {
  def kids = getChildDevices() ?: []
  if (!kids) return "No child devices yet."
  kids.collect { "• ${it.displayName} (DNI: ${it.deviceNetworkId})" }.join("\n")
}

def installed() {}
def updated() {}

private int updateAllChildrenHost(String host) {
  int count = 0
  (getChildDevices() ?: []).each { child ->
    child.updateSetting("roomieHost", [type:"string", value: host])
    count++
  }
  state.lastHost = host
  return count
}

private String runImport() {
  def rows = parseCsv(csvText ?: "")
  if (!rows) return "No valid rows found."

  def created = []
  def updated = []

  rows.each { row ->
    String name  = row.Name?.trim()
    String onId  = row.OnUUID?.trim()
    String offId = row.OffUUID?.trim()
    if (!name || !onId || !offId) return

    String slug = name.toLowerCase().replaceAll('[^a-z0-9]+','-')
    String shortOn = onId.replaceAll('[^A-Fa-f0-9-]','').take(8).toLowerCase()
    String dni = "roomie:${slug}-${shortOn}"

    def child = getChildDevice(dni)
    if (!child) {
      child = addChildDevice("jc.roomie", "Roomie Activity Switch", dni, [label: name, isComponent: false])
      created << name
    } else {
      if (child.displayName != name) child.setLabel(name)
      updated << name
    }

    child.updateSetting("roomieHost", [type:"string", value: roomieHost])
    child.updateSetting("uuidOn",     [type:"string", value: onId])
    child.updateSetting("uuidOff",    [type:"string", value: offId])
    child.updateSetting("autoReset",  [type:"bool",   value: autoResetDefault])
    child.updateSetting("debugLog",   [type:"bool",   value: debugDefault])
  }

  def msg = new StringBuilder("Import complete.<br>")
  if (created) {
    msg << "<b>Created (${created.size()}):</b><br>"
    created.each { msg << "• ${it}<br>" }
  }
  if (updated) {
    msg << "<b>Updated (${updated.size()}):</b><br>"
    updated.each { msg << "• ${it}<br>" }
  }
  if (!created && !updated) msg << "No changes detected.<br>"

  return msg.toString()
}

private List<Map> parseCsv(String text) {
  def lines = (text ?: "")
    .readLines()
    .collect { it.trim() }
    .findAll { it && !it.startsWith("#") }   // ignore empty and comment lines
  if (!lines) return []

  // Detect optional header (case-insensitive)
  def headerRegex = ~/(?i)^\s*name\s*,\s*onuuid\s*,\s*offuuid\s*$/
  boolean hasHeader = (lines[0] =~ headerRegex).matches()
  def dataLines = hasHeader ? lines.drop(1) : lines

  def rows = []
  dataLines.eachWithIndex { ln, idx ->
    def cols = ln.split(/\s*,\s*/, -1)
    if (cols.size() < 3) {
      log.warn "CSV row ${idx + 1}${hasHeader ? ' (after header)' : ''} ignored: needs 3 columns -> ${ln}"
      return
    }
    def name  = cols[0]?.trim()
    def onId  = cols[1]?.trim()
    def offId = cols[2]?.trim()

    if (!name || !onId || !offId) {
      log.warn "CSV row ${idx + 1} ignored: blank column(s) -> ${ln}"
      return
    }

    def uuidRe = ~/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/
    if (!(onId ==~ uuidRe) || !(offId ==~ uuidRe)) {
      log.warn "CSV row ${idx + 1} warning: UUID format not standard -> on='${onId}', off='${offId}'"
    }

    rows << [Name: name, OnUUID: onId, OffUUID: offId]
  }
  return rows
}
