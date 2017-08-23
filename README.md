# carmon
This is a simple Android application whats implements Vehicle monitoring feature.

Main goal to be a minification power consuming. Application wakeup (toggle off Airplane mode) at scheduled time, determines current location via GPS/Cell and send it to MQTT server in Owntracks format. User may controll app by sending commands to MQTT topic.
