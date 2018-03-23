package com.example.npm

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients

import groovy.json.JsonOutput

class PackageGroovy {
  static main(args) {
    def pkg = 'vala'
    def vsn = '1.7.0'

    def packageJson = [
      name: pkg,
      version: vsn,
      description: 'Demo package',
      license: 'ISC',
      keywords: [ 'demo' ]
    ]

    println JsonOutput.toJson(packageJson)
    def pkgJsonBytes = JsonOutput.toJson(packageJson).bytes

    def tae = new TarArchiveEntry("package/package.json")
    tae.setSize(pkgJsonBytes.length)

    def bos = new ByteArrayOutputStream()
    def taos = new TarArchiveOutputStream(new GzipCompressorOutputStream(bos))

    taos.putArchiveEntry(tae)
    taos.write(pkgJsonBytes)
    taos.closeArchiveEntry()
    taos.close()

    def data = bos.toByteArray().encodeBase64().toString()

    def credProvider = new BasicCredentialsProvider()
    credProvider.setCredentials(
        new AuthScope("localhost", 8081),
        new UsernamePasswordCredentials("admin", "admin123"))
    def http = HttpClients.custom()
        .setDefaultCredentialsProvider(credProvider)
        .build()

    def pkgTgz = "${pkg}-${vsn}.tgz"

    def shasum = MessageDigest.getInstance("SHA-1").digest(bos.toByteArray()).encodeHex().toString()

    // add dist to package json
    packageJson['_id'] = "${pkg}@${vsn}"
    packageJson['dist'] = [
      shasum: shasum,
      tarball: "http://com.example/${pkgTgz}"
    ]

    def pkgRoot = [
      _id: pkg,
      name: pkg,
      description: 'Demo package',
      'dist-tags': [ latest: vsn ],
      versions: [ "${vsn}": packageJson ],
      _attachments: [
        "${pkgTgz}": [
          'content-type': ContentType.APPLICATION_OCTET_STREAM,
          data: data,
          length: data.length()
        ]
      ]
    ]

    println JsonOutput.toJson(pkgRoot)
    def request = JsonOutput.toJson(pkgRoot).bytes

    def httpput = new HttpPut("http://localhost:8081/repository/staging-npm-build/${pkg}")
    httpput.setEntity(new ByteArrayEntity(request, ContentType.APPLICATION_JSON))

    def response = http.execute(httpput)
    println response
  }
}
