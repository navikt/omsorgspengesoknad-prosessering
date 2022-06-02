FROM gcr.io/distroless/java17

COPY build/libs/app.jar ./

CMD ["app.jar"]