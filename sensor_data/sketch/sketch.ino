#include <Arduino_Modulino.h>
#include <Arduino_RouterBridge.h>

ModulinoThermo thermo;
ModulinoDistance distance;

// I2C functions to call from bridge RPC
float get_temperature() 
{
  return thermo.getTemperature();
}

int get_distance() 
{
  if (distance.available()) 
  {
    return distance.get();
  }
  return -1; // Return false number for incorrect readings, idk
}

void setup() 
{
  Serial.begin(115200);

  Bridge.begin();
  Modulino.begin();
  thermo.begin();
  distance.begin();

  Bridge.provide("get_temperature", get_temperature);
  Bridge.provide("get_distance", get_distance);
}

void loop() 
{
  // Leave empty
}
