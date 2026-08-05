"""Fetch data from the sensor and context APIs."""

from context_client import fetch_api_data


SENSOR_DATA_URL = "http://10.73.51.136:9000/data"
CONTEXT_URL = "http://10.73.51.106:8080/context"


def main() -> None:
    sensor_data = fetch_api_data(SENSOR_DATA_URL)
    context_data = fetch_api_data(CONTEXT_URL)

    print(sensor_data)
    print(context_data)


if __name__ == "__main__":
    main()
