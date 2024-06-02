# Uruchom klaster poniższą komendą

```sh
gcloud dataproc clusters create ${CLUSTER_NAME} \
--enable-component-gateway --region ${REGION} --subnet default \
--master-machine-type n1-standard-4 --master-boot-disk-size 50 \
--num-workers 2 --worker-machine-type n1-standard-2 --worker-boot-disk-size 50 \
--image-version 2.1-debian11 --optional-components DOCKER,ZOOKEEPER \
--project ${PROJECT_ID} --max-age=3h \
--metadata "run-on-master=true" \
--initialization-actions \
gs://goog-dataproc-initialization-actions-${REGION}/kafka/kafka.sh
```

# Dokonaj edycji zmiennych środowiskowych w pliku env-setup.sh

# Po wykonaniu zmian, uruchom poniższy skrypt:
```sh
./main.sh
```
# Teraz wystarczy uruchomić skrypty producenta i konsumenta
```sh
./producer.sh
./consumer.sh
```
