#!/bin/sh
/usr/bin/mc config host add myminio http://minio:9000 minioadmin minioadmin;
/usr/bin/mc mb -p myminio/careervault-assets;
/usr/bin/mc policy set download myminio/careervault-assets;
exit 0;