
# README 
此專案原由學習mini kubernatites，為滿足練習
- build an java Application deploy to minikube
- 讓三種不同性質 Pod image 同時在 minikube 連線共享資源
### 參考資料
https://learnk8s.io/spring-boot-kubernetes-guide

## Knote

### 介紹
- SprintBoot專案 + mongoDB + minIO
- 上傳文字存到mongoDB
- 上傳檔案到minIO

### 注意事項
- 不要修改SpringBoot版本，會導致404網頁出不來，可能是CommonMark元件與SpringBoot先進版本會起衝突

### 依存性
- 先安裝 mongo + minIO
  - 安裝```docker pull mongo```
  - 安裝```docker pull minio/minio```
- 執行mongoDB 
  - ```docker run -d --hostname mongo --rm --name mongo -p 27017:27017 mongo:latest```
- 執行minio 
  - ```docker run -d --hostname minio --rm --name=minio -p 9000:9000 -e MINIO_ACCESS_KEY=mykey -e MINIO_SECRET_KEY=mysecret minio/minio server /data```

### 編譯
``` 
mvn clean package -Dmaven.test.skip=true
```
### 執行
``` 
mvn spring-boot:run -Dspring-boot.run.arguments="--MINIO_ACCESS_KEY=mykey,--MINIO_SECRET_KEY=mysecret" 
```

### 畫面

http://localhost:8080/
![img.png](img.png)

### 取得專案
```bash
git clone https://github.com/jackcomtw/SpringBoot_mongoDB.git
```
### 環境變數說明
```env
MONGO_URL= monoDB連線的主機，預設值:mongodb://localhost:27017/dev
MINIO_ACCESS_KEY= minio連線的access key（必填）
MINIO_SECRET_KEY= minio連線的secret key（必填）
```

# Docker整合執行
```
docker pull openjdk:11-jre
docker build -t knote-java --pull=false .
docker network create knote
docker run -d --name=mongo --rm --network=knote mongo
docker run -d --name=minio --rm --network=knote -e MINIO_ACCESS_KEY=mykey -e MINIO_SECRET_KEY=mysecret minio/minio server /data
docker run -d --name=knote-java --rm --network=knote -p 8080:8080 -e MONGO_URL=mongodb://mongo:27017/dev -e MINIO_HOST=minio  -e MINIO_ACCESS_KEY=mykey -e MINIO_SECRET_KEY=mysecret knote-java 
```
> 如果無法啟動mongoDB，可能是docker版本太舊
> https://docs.docker.com/engine/install/ubuntu/


# 停止
```
docker stop mongo minio knote-java
```



# 聯絡作者
