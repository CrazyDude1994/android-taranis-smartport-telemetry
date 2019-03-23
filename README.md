# Android Taranis SmartPort Telemetry Viewer

This android application let you view and record your taranis telemetry data in realtime.

![alt text](https://raw.githubusercontent.com/CrazyDude1994/android-taranis-smartport-telemetry/master/screen.jpg "Screenshot")

# Hardware and connection

In order this to work you need additional hardware: inverter and bluetooth module (HC-05 or HC-06 or something else)
One important thing: Module should be configured to work on 57600 baud rate, otherwise it won't work. 
Connect inverter to your Smart Port and then connect bluetooth module to the inverter. You now can connect your phone to your bluetooth module and view data

![alt text](https://raw.githubusercontent.com/CrazyDude1994/android-taranis-smartport-telemetry/master/connection.jpg "Connection example")

## HC-06 With MOSFET inverter

![Inverter diagram](inverter.png)

![HC-06 With inverter](hc06_inverter.JPG)

## HC-06 Configuration

By default, HC-06 is configured for 9600bps. To configure, use any USB-to-serial converter and serial client. Mac and Linux can use Terminal `screen` command. Windows users can use [Putty](http://www.putty.org/)

HC-06 module will expect that each command will be entered very fast (AFAIR max 1s between letters). So the best option here is to open text editor, type commands there and then copy them one by one into serial software.

1. `AT+NAMEyournamehere` - no spaces!
1. `AT+PIN1234` - PIN, no spaces again
1. `AT+ENABLEIND0`
1. `AT+BAUD7` - set port speed to 57600


# Google Play
https://play.google.com/store/apps/details?id=crazydude.com.telemetry

# Say thanks
If you want to help or say thanks, best way is just to follow my FPV youtube channel
https://www.youtube.com/channel/UCjAhODF0Achhc1fynxEXQLg?view_as=subscriber
