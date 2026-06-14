# STM32 Message Protocol

The Android app reads a single incoming text stream from the rover STM32 through the HC-05 module.

The stream contains:

- Standard NMEA sentences from the ZED-F9P
- STM32 control/status messages beginning with `#`

Unknown `#` messages should be ignored by the app.

## NMEA Sentences

The app currently uses:

```text
$GNGGA
$GNRMC
$GNVTG
```

`GGA` is used for current latitude, longitude, fix label, and satellite count.

`RMC` and `VTG` can be used for course/heading when moving.

## Point Status

STM32 announces the next point:

```text
#POINT_NEXT,P053,ACTIVE=0
```

STM32 announces point averaging start:

```text
#POINT_START,P053
```

STM32 announces live averaging status:

```text
#POINT_STATUS,P053,READS=42,HD_SD=0.012,VD_SD=0.018
```

STM32 announces stored point:

```text
#POINT_STORE,P053,37.752057611,-121.385391987,100.345,READS=86,FIX=3D,CARRIER=2,HACC=12,VACC=18,UBX=P053.UBX,TYPE=GCP
```

## Phone-to-STM32 Commands

Toggle point start/store:

```text
#POINT_TOGGLE,<sequence>
```

Delete point:

```text
#POINT_DELETE,P053
```

Update rod height:

```text
#ROD,2.000
```

Clear SD card while preserving `ROD.TXT`:

```text
#SD_CLEAR
```

## STM32 Replies

Command acknowledged:

```text
#POINT_TOGGLE_OK,<sequence>
```

Point delete success:

```text
#POINT_DELETE_OK,P053
```

Point delete error:

```text
#POINT_DELETE_ERR,P053,<code>
```

Rod height accepted:

```text
#ROD_OK,2.000
```

Rod height error:

```text
#ROD_ERR
```

SD clear queued:

```text
#SD_CLEAR_QUEUED
```

SD clear started:

```text
#SD_CLEAR_START
```

SD clear progress:

```text
#SD_CLEAR_PROGRESS,12
```

SD clear complete:

```text
#SD_CLEAR_OK
```

SD clear error:

```text
#SD_CLEAR_ERR,<code>
```
