TRANSFER_FILE=$PWD/transfer.sql
touch $TRANSFER_FILE
sudo chown postgres $TRANSFER_FILE
sudo -u postgres pg_dump --file=$TRANSFER_FILE --format=custom --dbname=blynk --table=users --table=flashed_tokens
ls -l $TRANSFER_FILE