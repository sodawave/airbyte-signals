FROM airbyte/python-connector-base:1.1.0

WORKDIR /airbyte/integration_code
COPY . ./
RUN pip install --no-cache-dir .

ENTRYPOINT ["python", "/airbyte/integration_code/main.py"]
