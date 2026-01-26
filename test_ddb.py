import boto3
dynamodb = boto3.client('dynamodb', endpoint_url='http://localhost:8000', region_name='ap-northeast-2', aws_access_key_id='dummy', aws_secret_access_key='dummy')
print("Tables:", dynamodb.list_tables()['TableNames'])
