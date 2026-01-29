import os
import boto3

endpoint = os.getenv("DYNAMODB_ENDPOINT", "")
region = os.getenv("AWS_REGION", "ap-northeast-2")

kwargs = {"service_name": "dynamodb", "region_name": region}
if endpoint:
    kwargs["endpoint_url"] = endpoint

dynamodb = boto3.client(**kwargs)
print("Tables:", dynamodb.list_tables().get("TableNames", []))
