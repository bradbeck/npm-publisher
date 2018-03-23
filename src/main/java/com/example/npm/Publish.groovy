package com.example.npm

import java.security.MessageDigest

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClients

import groovy.json.JsonOutput

class Publish {
  static main(args) {
    def pkg = 'vala'
    def vsn = '1.16.0'
    def host = 'localhost'
    def port = 8081
    def repo = "http://${host}:${port}/repository/staging-npm-build"
    def user = 'admin'
    def pass = 'admin123'

    def packageJson = [
      name: pkg,
      version: vsn,
      description: 'Demo package',
      license: 'ISC',
      keywords: [ 'demo' ]
    ]

    def pkgJsonJson = JsonOutput.toJson(packageJson)
    println JsonOutput.prettyPrint(pkgJsonJson)
    def pkgJsonBytes = pkgJsonJson.bytes

    def bos = new ByteArrayOutputStream()
    def taos = new TarArchiveOutputStream(new GzipCompressorOutputStream(bos))

    def tae = new TarArchiveEntry("package/package.json")
    tae.size = pkgJsonBytes.length

    taos.putArchiveEntry(tae)
    taos.write(pkgJsonBytes)
    taos.closeArchiveEntry()
    taos.close()

    def data = bos.toByteArray()

    def credProvider = new BasicCredentialsProvider()
    credProvider.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(user, pass))
    def http = HttpClients.custom().setDefaultCredentialsProvider(credProvider).build()

    def pkgTgz = "${pkg}-${vsn}.tgz"

    def shasum = MessageDigest.getInstance("SHA-1").digest(data).encodeHex().toString()

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
          'content-type': 'application/octet-stream',
          data: data.encodeBase64().toString(),
          length: data.length
        ]
      ]
    ]

    def pkgRootJson = JsonOutput.toJson(pkgRoot)
    println JsonOutput.prettyPrint(pkgRootJson)
    def request = pkgRootJson.bytes

    def httpput = new HttpPut("${repo}/${pkg}")
    httpput.entity = new ByteArrayEntity(request, ContentType.APPLICATION_JSON)

    def response = http.execute(httpput)
    println response
  }
}
