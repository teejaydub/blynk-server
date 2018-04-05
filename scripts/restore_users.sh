TRANSFER_FILE=$PWD/transfer.sql
sudo chown postgres $TRANSFER_FILE
sudo -u postgres pg_restore --verbose --clean $TRANSFER_FILE --dbname=blynk
