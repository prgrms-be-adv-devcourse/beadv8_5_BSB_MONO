# 공식 Elasticsearch 이미지에 한글 형태소 분석기(nori) 플러그인만 추가
# 버전은 Spring Boot가 관리하는 ES 클라이언트 버전(9.4.5)과 맞춘다
FROM elasticsearch:9.4.5
RUN bin/elasticsearch-plugin install --batch analysis-nori
