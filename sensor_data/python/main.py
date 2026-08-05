import time
from arduino.app_utils import App, Bridge

print("Sensor collecton starting ")

class ContextBuilder:
    pass

def sensor_polling_loop():
    try:
        # Make RCP calls (this is blocking)
        temperature = Bridge.call("get_temperature")
        distance = Bridge.call("get_distance")
        
        print("--- Modulino Telemetry Received ---")
        print(f"Temperature: {temperature} C")
        print(f"Distance   : {distance} mm")

        # send over network json object with context from sensor readings
        if node.get_phase() == "Operational":
            peers = node.get_peers()
            print(f"Active peers in cluster: {peers}")
            
            # Broadcast data to all peers
            node.broadcast_data({"task": "sync", "status": "ready"})
            
            # Send unicast message to a specific peer
            #if peers:
            #    node.send_data(target_mac=peers[0], payload="Hello direct peer!")
        else:
            printf(f"Node is not operational")
        
    except Exception as e:
        print(f"[ERROR] Bridge reading failed: {e}")
        
    time.sleep(1)

if __name__ == "__main__":    
    # Hand over execution to the App
    App.run(user_loop=sensor_polling_loop)
