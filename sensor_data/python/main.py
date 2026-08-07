import time
import json
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from arduino.app_utils import App, Bridge
from datetime import datetime
from zoneinfo import ZoneInfo

# Global variable to store the latest sensor data
latest_telemetry = {
    "temperature": None,
    "distance": None,
    "status": "No data yet"
}

class SensorHTTPRequestHandler(BaseHTTPRequestHandler):
    """Handles HTTP requests and serves the latest sensor data as JSON."""
    def do_GET(self):
        if self.path == '/data':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            
            # Serve the global sensor data
            response_json = json.dumps(latest_telemetry)
            self.wfile.write(response_json.encode('utf-8'))
        else:
            # Fallback for other paths
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")

def sensor_polling_loop():
    global latest_telemetry
    try:
        # Make RPC calls (blocking)
        temperature = Bridge.call("get_temperature")
        distance = Bridge.call("get_distance")
        
        #print("--- Modulino Telemetry Received ---")
        #print(f"Temperature: {temperature} C")
        #print(f"Distance   : {distance} mm")

        # Update the global telemetry dictionary
        latest_telemetry = {
            "temperature": temperature,
            "distance": distance,
            "timestamp": datetime.now(ZoneInfo("America/Los_Angeles")).strftime("%Y-%m-%d %H:%M:%S"),
        }
        
    except Exception as e:
        print(f"[ERROR] Bridge reading failed: {e}")
        latest_telemetry["status"] = f"Error: {e}"

def start_arduino_app():
    """Target function to run the Arduino App loop in a background thread."""
    print("Sensor collection starting...")
    
    App.run(user_loop=sensor_polling_loop)

if __name__ == "__main__":    
    arduino_thread = threading.Thread(target=start_arduino_app, daemon=True)
    arduino_thread.start()

    PORT = 9000
    server_address = ('', PORT)
    httpd = HTTPServer(server_address, SensorHTTPRequestHandler)
    print(f"Serving sensor data on port {PORT} at http://localhost:{PORT}/data ...")
    
    try:
        # Run the server loop indefinitely
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down server.")
        httpd.server_close()
