# wrp
This application loads the websiteregister from the Dutch government. The data is persistent in memory for 15 minutes after first retrieving it. After that, it's recreated upon request of the data.

# Endpoints
`/registerdata` retrieves the data as json. Example response:
```json
[
    {
        "URL": "http://www.rijksoverheid.nl",
        "Organisatietype": "Rijksoverheid",
        "Organisatie": "AZ",
        "Suborganisatie": "DPC",
        "Afdeling": "Online Advies",
        "Bezoeken/mnd": "21.513.009",
        "Voldoet": "ja",
        "Totaal": "ja",
        "IPv6": "ja",
        "DNSSEC": "ja",
        "HTTPS": "ja",
        "CSP": "waarschuwing",
        "RefPol.": "ja",
        "X-Cont.": "ja",
        "X-Frame.": "ja",
        "Testdatum": "22-08-2022",
        "STARTTLS en DANE": "",
        "DMARC": "ja",
        "DKIM": "",
        "SPF": "ja",
        "Platformgebruik": "Platform Rijksoverheid Online (AZ)"
    },
    {
        "URL": "http://coronadashboard.rijksoverheid.nl",
        "Organisatietype": "Rijksoverheid",
        "Organisatie": "VWS",
        "Suborganisatie": "",
        "Afdeling": "Kerndepartement/PDC19",
        "Bezoeken/mnd": "4.746.286",
        "Voldoet": "ja",
        "Totaal": "ja",
        "IPv6": "ja",
        "DNSSEC": "ja",
        "HTTPS": "ja",
        "CSP": "waarschuwing",
        "RefPol.": "ja",
        "X-Cont.": "ja",
        "X-Frame.": "ja",
        "Testdatum": "26-08-2022",
        "STARTTLS en DANE": "",
        "DMARC": "ja",
        "DKIM": "ja",
        "SPF": "ja",
        "Platformgebruik": "StandaardPlatform CloudServices (BZK/Logius)"
    }
]
```
`/metadata` returns the metadata, linke columnheaders. Example response:
```json
{
    "documentURL": "https://www.communicatierijk.nl/binaries/communicatierijk/documenten/publicaties/2016/05/26/websiteregister/websiteregister-rijksoverheid-2022-08-26.ods",
    "discoveryDateTimeUTC": "2022-08-27T13:04:12.689Z",
    "registersFound": 1791,
    "columnHeaders": [
        "URL",
        "Organisatietype",
        "Organisatie",
        "Suborganisatie",
        "Afdeling",
        "Bezoeken/mnd",
        "Voldoet",
        "Totaal",
        "IPv6",
        "DNSSEC",
        "HTTPS",
        "CSP",
        "RefPol.",
        "X-Cont.",
        "X-Frame.",
        "Testdatum",
        "Totaal",
        "IPv6",
        "DNSSEC",
        "STARTTLS en DANE",
        "DMARC",
        "DKIM",
        "SPF",
        "Testdatum",
        "Platformgebruik"
    ]
}
```
`/checkfornew` triggers a manual search if a new version of the register is available. The application scans for a new version every hour, if a new version is found, then the callbackurl (if specified) is being called.

# Docker
```dockerfile
version: "3.7"
services:
  wrp:
    image: mrhoeve/wrp:latest
    container_name: wrp
    ports:
      - 8080:8080
    environment:
      resourceurl: https://resource.url/containing/ods/file
      callbackurl: https://some.url.to/callback
```
# Environment variables
`resourceurl` defaults to `https://www.communicatierijk.nl/vakkennis/rijkswebsites/verplichte-richtlijnen/websiteregister-rijksoverheid`. Use `callbackurl` to specify the page that must be notified when a new datafile is detected.
