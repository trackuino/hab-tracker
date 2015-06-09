# Hab Tracker

An Android application to predict and monitor high altitude / sounding balloon trajectories in a way similar to other prediction tools like:

  * [Balloon trajectory forecasts](http://weather.uwyo.edu/polar/balloon_traj.html), at the University of Wyoming.
  * [Landing predictor](http://habhub.org/predict/), at the University of Cambridge
  * [On-Line Near Space Flight Track Prediction Utility](http://nearspaceventures.com/w3Baltrak/readyget.pl), by the Near Space Ventures team.

The app grabs sounding data for your area from NOAA and plots the trajectory on a map given the estimate ascent/descent rates and burst altitude.

What makes this app special and different from other tools is that Hab Tracker collects real-time data from the APRS network and replaces NOAA sounding data with **actual wind speeds reported by the payload itself**. This makes prediction much more accurate as the flight progresses, especially because NOAA usually reports wind data only up to 26 Km high.

## APRS-DB ##

To use the real-time monitor and prediction feature you need an [APRS-DB](https://github.com/trackuino/aprsdb) server to get APRS data from while on the field. You don't need an APRS-DB server if you are only interested in pre-flight predictions based on NOAA sounding data.

## Building ##

The repository includes an Eclipse project you can import into your workspace. Look into the Payload/bin folder for a precompiled APK.
