# Home Made OMEMO Client

This is a repository containing the source code to my
[blog post](https://blogs.fsfe.org/vanitasvitae/2017/06/14/homemo/) about how to
build a home-made OMEMO chat application in less than 200 lines of code.

## Setup
You can clone the repository using `git clone https://git.fsfe.org/vanitasvitae/HomeMadeOmemo.git`

Next navigate to the root folder and compile using
```
cd HomeMadeOmemo/de.vanitasvitae.homemadeomemo
./gradlew build
```

## Run
You can run the application as follows:
```
java -jar build/libs/de.vanitasvitae.homemadeomemo.jar user@server.tld password
```
