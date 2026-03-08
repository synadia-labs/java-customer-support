: Change into my temp directory and remove any previous source dir
cd \tmp
rd /S /Q nats-server 

: Clone the repo then build
git clone https://github.com/nats-io/nats-server.git
cd nats-server
go get
go build main.go

: Move to my PATH
copy /Y main.exe \programs\bin\nats-server.exe
