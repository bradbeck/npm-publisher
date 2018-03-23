package com.example.npm;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Package
{
  static final ObjectMapper mapper = new ObjectMapper();
  
  public static void main(String[] args) throws Exception {
    String pkg = "vala";
    String vsn = "1.6.0";
    Map<String, Object> packageJson = new HashMap<>();
    packageJson.put("name", pkg);
    packageJson.put("version", vsn);
    packageJson.put("description", "Demo package");
    packageJson.put("license", "ISC");
    packageJson.put("keywords", Arrays.asList("demo"));
    
    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(packageJson));
    byte[] pkgJsonBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(packageJson).getBytes("UTF-8");
    
    TarArchiveEntry tae = new TarArchiveEntry("package/package.json");
    tae.setSize(pkgJsonBytes.length);
    
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    GzipCompressorOutputStream gcos = new GzipCompressorOutputStream(bos);
    TarArchiveOutputStream taos = new TarArchiveOutputStream(gcos);
    
    taos.putArchiveEntry(tae);
    taos.write(pkgJsonBytes);
    taos.closeArchiveEntry();
    taos.close();
    
    String data = Base64.encodeBase64String(bos.toByteArray());

    CredentialsProvider credProvider = new BasicCredentialsProvider();
    credProvider.setCredentials(
        new AuthScope("localhost", 8081),
        new UsernamePasswordCredentials("admin", "admin123"));
    CloseableHttpClient http = HttpClients.custom()
        .setDefaultCredentialsProvider(credProvider)
        .build();
    
    String pkgTgz = pkg + "-" + vsn + ".tgz";
    
    String shasum = new String(Hex.encodeHex(MessageDigest.getInstance("SHA-1").digest(bos.toByteArray())));
    
    // add dist to package json
    packageJson.put("_id", pkg + "@" + vsn);
    packageJson.put("dist", new HashMap<String, Object>() {{
      put("shasum", new String(shasum));
      put("tarball", "http://com.example/" + pkgTgz);
    }});
    
    Map<String, Object> pkgRoot = new HashMap<>();
    pkgRoot.put("_id", pkg);
    pkgRoot.put("name", pkg);
    pkgRoot.put("description", "Demo package");
    pkgRoot.put("dist-tags", new HashMap<String, Object>() {{ put("latest", vsn); }});
    pkgRoot.put("versions", new HashMap<String, Object>() {{
      put(vsn, packageJson);
    }});
    pkgRoot.put("_attachments", new HashMap<String, Object>() {{
      put(pkgTgz, new HashMap<String, Object>() {{
        put("content-type", "application/octet-stream");
        put("data", data);
        put("length", data.length());
      }});
    }});
    
    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pkgRoot));
    byte[] request = mapper.writeValueAsString(pkgRoot).getBytes();

    HttpPut httpput = new HttpPut("http://localhost:8081/repository/staging-npm-build/" + pkg);
    httpput.setEntity(new ByteArrayEntity(request, ContentType.APPLICATION_JSON));
    
    CloseableHttpResponse response = http.execute(httpput);
    System.out.println(response);
  }
}
