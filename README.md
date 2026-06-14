# F9P Rover Logger Android App

This app is intended to be used with the matching STM32 rover firmware and the STM32 base logger firmware:

```text
Base firmware:  https://github.com/jesrockr/stm32_f9p_gnss_base_logger
Rover firmware: https://github.com/jesrockr/stm32_f9p_gnss_rover_logger
```

Android companion app for a low-cost STM32 + ZED-F9P RTK rover logger.

The STM32 remains the trusted field logger. It records raw `.UBX` files and point CSV files to the SD card. This Android app connects to the rover over an HC-05 Bluetooth module and provides a live field interface for viewing GNSS status, storing points, reviewing points on a simple map, attaching photos, and exporting project data.

## Features

- BLE connection to HC-05 serial bridge
- Live NMEA position display
- Live map with current rover position
- Stored point markers with fix-status colors
- Start/store point control from the phone
- Physical STM32 button support
- Point averaging status with live read count and spread display
- GCP / TOPO point type selection
- Per-project CSV storage on the phone
- Matching point CSV workflow with STM32 SD card logging
- Point photo capture and thumbnail preview
- Delete-point command with phone/app/STM32 coordination
- Clear SD card command that preserves `ROD.TXT`
- Rod height prompt and STM32 `ROD.TXT` update
- Sound and vibration toggles

## Companion Hardware

This app is designed for the rover half of a base/rover setup:

- ZED-F9P GNSS receiver
- STM32 rover logger firmware
- SD card on STM32
- SSD1306 OLED on STM32
- HC-05 BLE/serial module connected to the STM32
- Android phone or tablet

The STM32 firmware handles raw GNSS logging and point averaging. The phone app is the operator interface.


## Build Requirements

- Android Studio
- Android SDK
- Android device with Bluetooth/BLE support
- Minimum Android SDK: 23
- Target SDK: 36

## Build in Android Studio

1. Open Android Studio.
2. Select `Open`.
3. Open this repository folder.
4. Let Gradle sync.
5. Connect an Android device with USB debugging enabled.
6. Click `Run`.

The debug APK will be generated under:

```text
app/build/outputs/apk/debug/
```

## First Use

1. Power the rover hardware.
2. Make sure the HC-05 module is connected to the STM32 rover UART.
3. Open the app.
4. Choose or create a project.
5. Allow Bluetooth/location permissions when prompted.
6. Connect to the HC-05.
7. Confirm NMEA data begins streaming.
8. Enter rod height when prompted.
9. Start collecting points.

## Field Workflow

Recommended workflow:

1. Start a new project for each job or field session.
2. Confirm the rover has a good fix before storing points.
3. Select `GCP` or `TOPO`.
4. Press `Start Point`.
5. Let the point average for the desired time.
6. Press `Store Point`.
7. Optionally attach a photo.
8. Keep the STM32 SD card files for backup PPK and audit trail.

## Phone Project Files

The app stores project data on the phone, including:

```text
POINTxxx.CSV
photos/
```

Photos are named to match the point they belong to.

## STM32 / Phone Data Relationship

The phone app and STM32 SD card both track point data. The STM32 is still the primary field logger because it records:

- Raw `.UBX` files for backup/PPK
- Point CSV files
- Rod height from `ROD.TXT`

The phone app mirrors and displays the data for field usability.

If old phone projects are opened after the SD card has been cleared, old phone points may still appear in the project, but matching SD raw logs may no longer exist. Treat the SD card as the field record.

## STM32 Message Protocol

The app listens for NMEA sentences plus STM32 control messages beginning with `#`.

See:

```text
docs/STM32_PROTOCOL.md
```

## Current Limitations

- No basemap yet; the live map is a local relative field map.
- Offline MBTiles basemap support is a future feature.
- The app assumes the matching STM32 rover firmware protocol.
- Bluetooth behavior can vary by HC-05 clone and Android device.
- This is field-test software, not a certified survey controller.

## Safety Notes

For survey work:

- Confirm the receiver fix type before storing points.
- Confirm rod height before each session.
- Keep raw `.UBX` files from the SD card.
- Do not rely only on phone-side cached project data.
- Re-observe critical points when practical.

