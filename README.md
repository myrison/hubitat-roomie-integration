# Roomie Remote + Hubitat: For Easy Integration With Alexa

**Background:** Amazon [dropped support](https://forum.roomieremote.com/t/original-alexa-skill-will-stop-working-on-november-1-2025/6154) for Roomie Remote's original Simple Control skill in November, 2025.  Their other Alexa skill "Roomie Remote" suffers from various usability challenges that make it cumbersome to use (at the time of this repo's creation, Alexa insists on starting every conversation with "handing off the request to the "Roomie Remote app," or something similar, making the simple act of turning on the TV far more involved than it should be.

**Goals:** 
- Say “Alexa, turn on _X_” to start a Roomie activity by UUID, and “turn off _X_” to call the room’s **System Off** UUID.  Alexa responds with nothing more than a confirmation beep and the device turns on.
- Simplify import of many devices at once
- No reliance on managing existing state or on state change.  Simply turn on/off based on the request.  

## What’s inside
- **Driver:** `Roomie Activity Switch` — maps `on()`/`off()` to Roomie HTTP POST `/api/v1/runactivity` with body `{"au":"<UUID>"}`.
- **App:** `Roomie Activity Importer` — paste CSV and bulk-create many switches at once.

## Install in Hubitat
1. Hubitat → **Drivers Code** → New Driver → paste `drivers/roomie-activity-switch.groovy` → **Save**.
2. Hubitat → **Apps Code** → New App → paste `apps/roomie-activity-importer.groovy` → **Save**.
3. Hubitat → **Apps** → **Add User App** → _Roomie Activity Importer_.

## Retrieve Roomie Activity IDs
1. Run API command to retrieve all current activities:

```bash
curl -X GET 'http://YOUR_LOCAL_ROOMIE_IP:47147/api/v1/activities'
```
Your response will look something like:
```text
{"st":"success","da":[{"icon":"logo-amazonfiretv","roomuuid":"37DEFD7F-8214-4274-B812-6B20D404FF8C","name":"Living Room Fire TV","uuid":"066CC010-1B66-44FA-AF39-76692F19051E"},{"icon":"logo-plex","roomuuid":"37DEFD7F-8214-4274-B812-6B20D404FF8C","name":"Living Room Apple TV","uuid":"6C4FB652-AFE2-4F3F-8AB8-07CB4F7235E6"}],"co":200}
```

2. Convert the API response into a CSV using the following columns `Name,OnUUID,OffUUID` (ask any AI model to do this if you have a lot of devices).
   Retain only the activity names you want to create in Hubitat.  Change the name to be the name you want to use to invoke the activity in Alexa.


## Configure in Hubitat
1. Hubitat → **Apps** → **Open Roomie Activity Importer**
2. Set **Roomie Host/IP** (e.g., `192.168.2.196`).
3. Paste the contents of the CSV into the Roomie Activity Importer in Hubitat where requested (header optional):
   ```csv
   Name,OnUUID,OffUUID
   Theater Plex,6C4FB652-AFE2-4F3F-8AB8-07CB4F7235E6,F1B1871F-B2B8-4245-A785-1F5C78272D16
   Living Room Apple TV,9D5C8C0D-5ACF-416D-917E-E7DDFBE1DA78,9457BFA2-79EA-4DBE-87C5-9189224386A4
4. Configure the options as you desire:
      - **"Auto-reset switches after commands?"** Useful in certain scenarios, not required., defaultValue: false
      - **"Enable driver debug logging?"**      defaultValue: true
      - **"Advertise as Light instead of Outlet?"** This allows Alexa to turn on/off your media device when you ask to turn all lights on or off, defaultValue: false
  
5. Click Import / Update devices - this will create a child device as a virtual switch for each item in your CSV

## Expose to Alexa in Hubitat

1. In Hubitat → Apps: Open the Amazon Echo Skill
2. Check the boxes to select the new child Roomie activities you created
3. Click Done
4. Sometimes Alexa auto-detects the new devices immediately, other times you have to say "Alexa, Discover new devices" to find them
5. Invoke one of your child devices by name to test "Alexa, turn on the Living Room Fire TV", then test the command to turn it off.

## Troubleshooting

If the commands don't work as expected, check the Hubitat logs page for the custom app to get more information.  The only problem I had setting this up was setting the local IP incorrectly (you don't need to include http or https, and you don't need to include the port number, they're set automatically)
   
