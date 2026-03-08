: Change into my temp directory and remove any previous source dir
cd \tmp
rd /S /Q nats-server

: Clone the repo, checkout the tag, then build
git clone https://github.com/nats-io/nats-server.git
cd nats-server
git checkout tags/%1 -b nstag
go get
go build main.go

: Move to my PATH
copy /Y main.exe \programs\bin\nats-server.exe
