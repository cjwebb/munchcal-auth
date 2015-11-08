FROM clojure

RUN mkdir /opt/auth
WORKDIR /opt/auth

COPY project.clj /opt/auth/
RUN lein deps

COPY . /opt/auth
RUN mv "$(lein ring uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" auth.jar

EXPOSE 3000
CMD ["java", "-jar", "auth.jar"]

