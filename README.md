# behave-maven-plugin

Add your Bintray credentials to the global maven settings.xml.

~/.m2/settings.xml

```
<settings>
  <servers>
    <server>
  	  <id>bintray</id>
  	  <username>USER</username>
  	  <password>API-KEY</password>
    </server>
  </servers>
</settings>
```

Then run mvn install deploy
