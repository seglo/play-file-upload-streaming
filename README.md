# Play File Upload Iteratee to Java OutputStream

[![Build Status](https://travis-ci.org/seglo/play-iteratee-to-outputstream.svg?branch=master)](https://travis-ci.org/seglo/play-iteratee-to-outputstream)

> A Play demo project that generates an MD5 hash for any file uploaded.

This is a small demo that demonstrates writing a Play `Iteratee` to an arbitrary `OutputStream`. This project will generate an MD5 hash of any file you upload to `uploadToHash` Play Action.

For more information on how this demo works see the accompany blog post: [Play File Upload Iteratee to Java OutputStream]().

This project was re-factored from Mike Slinn's `play21-file-upload-streaming` repo, [https://github.com/mslinn/play21-file-upload-streaming](https://github.com/mslinn/play21-file-upload-streaming)

## Run & Testing

To run the demo locally run `sbt run`

To run tests run `sbt test`

## Call endpoint with curl

```bash
$ curl -i --no-keepalive -F name=spark-1.3.0-bin-hadoop2.4.tgz -F filedata=@/home/seglo/Downloads/spark-1.3.0-bin-hadoop2.4.tgz http://localhost:9000/uploadToHash
HTTP/1.1 100 Continue

HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Date: Thu, 31 Dec 2015 22:30:24 GMT
Content-Length: 32

20DFFD5254A2B7E57B76A488CAB40CD8
```
