FROM toposoid/toposoid-scala-lib:0.5

WORKDIR /app
ARG TARGET_BRANCH
ARG JAVA_OPT_XMX
ENV DEPLOYMENT=local
ENV _JAVA_OPTIONS="-Xms512m -Xmx"${JAVA_OPT_XMX}

RUN git clone https://github.com/toposoid/toposoid-deduction-common.git \
&& cd toposoid-deduction-common \
&& git fetch origin ${TARGET_BRANCH} \
&& git checkout ${TARGET_BRANCH} \
&& sbt publishLocal \
&& rm -Rf ./target \
&& cd .. \
&& git clone https://github.com/toposoid/toposoid-deduction-unit-sentence-vector-match-web.git \
&& cd toposoid-deduction-unit-sentence-vector-match-web \
&& git fetch origin ${TARGET_BRANCH} \
&& git checkout ${TARGET_BRANCH} \
&& sbt playUpdateSecret 1> /dev/null \
&& sbt dist \
&& cd /app/toposoid-deduction-unit-sentence-vector-match-web/target/universal \
&& unzip -o toposoid-deduction-unit-sentence-vector-match-web-0.5.zip


COPY ./docker-entrypoint.sh /app/
ENTRYPOINT ["/app/docker-entrypoint.sh"]

