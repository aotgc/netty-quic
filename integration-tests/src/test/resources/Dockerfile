FROM golang:latest

ARG hash=9f5b96f3

RUN go get -v github.com/lucas-clemente/quic-go/...

WORKDIR src/github.com/lucas-clemente/quic-go

RUN mkdir -p /var/www
RUN touch /var/www/index.html

RUN git checkout $hash

EXPOSE 6121/udp

ENTRYPOINT ["go", "run", "example/main.go", "-v", "-bind", "0.0.0.0:6121", "-tcp" ]
