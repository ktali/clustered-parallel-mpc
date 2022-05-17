FROM openjdk:11-bullseye
RUN apt update && apt install -y ca-certificates apt-transport-https
RUN echo "deb https://repo.cyber.ee/sharemind/apt/2022.03 bullseye non-free" \
    > /etc/apt/sources.list.d/sharemind.list
RUN --mount=type=secret,id=sharemind-apt ln -s /run/secrets/sharemind-apt /etc/apt/auth.conf.d/sharemind.conf && \
    apt update && \
    apt install -y \
    libsharemind-controller-dev
COPY bin /gateway/bin
COPY lib /gateway/lib
WORKDIR /gateway/bin/
ENTRYPOINT ["./gateway"]
