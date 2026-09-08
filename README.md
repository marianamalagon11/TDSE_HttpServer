# Sequential HTTP Server — Networking Lab, Part 2
## Mariana Malagón Tochoy

## Lab summary

Extend a socket-based Java server into a small sequential web application that serves
HTML, JavaScript and images, exposes a few hardcoded service URLs, and runs on a single
AWS EC2 instance.

The server is deliberately limited: it handles one connection at a time. The point of the
laboratory is to build a correct, observable baseline and understand where requests wait,
before introducing concurrency or distribution. Asynchronous JavaScript keeps the browser
responsive, but it does not make the server concurrent; moving to EC2 changes where the
process runs, but one instance is still one capacity limit.



---

## Progress by lab section

### 1. Purpose and boundaries

**Scope.** Serve static resources with correct response metadata, recognise a small set of
hardcoded URLs that produce dynamic responses, and run the same application on one EC2
instance.

**Out of scope.** Threads, concurrent requests, thread pools, queues, load balancers,
autoscaling, containers, databases, authentication and production security. The server
processes one connection at a time.

**Prediction recorded before implementing (revisited in section 6.2).** While one user is
running a slow request, a second user's request will not be answered until the first one
finishes, because the server accepts a new connection only after closing the previous one.

---

### 2. Starting point: the minimal server

#### 2.1 Baseline verification

The server prints every line it receives, so the request line and headers sent by the
browser are visible in the console. A single page load produces more than one request:
besides the page itself, the browser asks for the favicon, and it would ask separately for
every script and image the page references.

The response is a status line, a content-type header, a blank line, and the body. The
blank line is what tells the client where the headers end.

![evidence](docs/evidenceLocal1.png)
![evidence2](docs/evidenceLocal2.png)

#### 2.2 Accept multiple sequential requests

The connection loop was changed so the process keeps accepting connections after
completing each response. The listening socket stays open for the lifetime of the
process; the client socket and its streams are closed after every response, using
try-with-resources so they are released even when the request fails.

Each connection is handled completely before the next one begins. This is
**repetition, not concurrency**: there is a single thread of execution, and a second
connection waits in the operating system's accept queue until the current one is
done.

The server log makes this visible. Requests never interleave — the headers of one
request are always fully consumed and answered before the next request line appears:
Three page reloads produced fifteen consecutive requests answered by the same running
process, without restarting it. This also satisfies the "repeated requests" row of the
section 6.1 test matrix.

![evidence 2.2](docs/evidence2.2.png)


---

### 3. Serve HTML, JavaScript and images

The home page, the stylesheet, the client script and both images are now real files
under `src/main/resources/public`. The server reads every resource as bytes and
announces its content type from the file extension, so text and binary resources
follow one response path.

A single page request produces five separate HTTP requests: the browser reads the
HTML, discovers the references to the stylesheet, the script and the images, and
asks for each one individually.

| Resource | Status | Content type |
|---|---|---|
| `index.html` | 200 | `text/html; charset=utf-8` |
| `styles.css` | 200 | `text/css; charset=utf-8` |
| `app.js` | 200 | `application/javascript; charset=utf-8` |
| `logoU.png` | 200 | `image/png` |
| `fotoU.jpeg` | 200 | `image/jpeg` |

![Network view showing five separate requests](docs/network-static.png)

Content length is computed from the byte array rather than from the number of
characters. This matters because a single accented character takes two bytes in
UTF-8: counting characters would announce a size the client never receives, and the
browser would either truncate the body or wait for bytes that never arrive.

#### Controlled error responses

Requests that cannot be served produce an explicit status instead of a generic
answer:
![responseErrors](docs/responseErrors.png)


Only `GET` is accepted for this laboratory, and a rejected method carries an `Allow`
header so the client knows what the server does support.

The traversal attempt is rejected before the file system is touched. The path is
URL-decoded first, so an attack written as `%2e%2e` cannot slip past the check, and
`pom.xml` is never disclosed. Backslashes and null bytes are rejected for the same
reason: the application is developed on Windows and deployed on Linux, and both
separators must be blocked to behave identically on either host.


---

### 4. Hardcoded service URLs

Four service paths are recognised with explicit conditions on the request path. There
is no routing framework, no annotations and no dependency injection: the mechanism
that selects behaviour stays visible, which is what this section of the lab is meant
to expose. Any path that is not one of these four is treated as a static resource.

| Path | Input | Success | Error |
|---|---|---|---|
| `/app/hello` | `name` in the query string | `200` with a JSON greeting | `400` when `name` is missing |
| `/app/square` | `n` in the query string | `200` with the input and its square | `400` when `n` is missing or not a number |
| `/app/time` | none | `200` with the current server time | — |
| `/app/health` | none | `200` with a status flag | — |

All four return `application/json`. None of them stores anything between requests:
every response is computed from the current request alone.

![Service responses, valid and invalid](docs/services.png)

**Validation.** Query parameters are split on `&` and `=` before being URL-decoded,
not after: decoding first would turn a `%26` inside a value into a real separator and
split the parameter in the wrong place. A missing or non-numeric parameter produces a
client-error status rather than a successful response carrying an error message.

**Escaping.** User input is never inserted into JSON as-is. A name containing a quote
would close the string early and break the response, so quotes, backslashes, control
characters and angle brackets are escaped before the value is embedded.

The server time is returned in ISO-8601 with its offset, so the value is clearly
produced by the server rather than read from the browser clock.
---

### 5. Asynchronous browser client

The home page is the client interface. Every action is handled by `app.js`, which
builds the service URL, sends the request with the browser's asynchronous request API,
and updates only the relevant part of the page. The document is never reloaded: in the
network view the page, the stylesheet, the script and the images are requested once,
while each button click adds a single `fetch` entry.

**Interface.** A text field and an action for the greeting, a numeric field and an
action for the square, an action for the server time, a result area, and a separate
error area. Both areas can hold content at the same time, so an invalid request does
not erase the last successful result.

![Successful asynchronous requests without page reload](docs/async-client.png)

**Handling the three outcomes.** The client distinguishes a network failure from a
valid HTTP error response from a successful one, because they need different messages:

- The request never reaches a server — no HTTP response exists — and the user is told
  the server could not be contacted.
- The request completes but the status indicates an error. This case needs an explicit
  check: `fetch` treats a `400` as a completed request and does not raise, so reading
  the body without checking the status first would display `undefined` instead of the
  problem. The status is verified before the body is interpreted, and the `error`
  field returned by the service is shown as the message.
- The request succeeds and the JSON values update the result area.

**Loading state.** Buttons are disabled and the result area announces that a response
is pending, so the interface visibly reacts while the request is in flight. The state
is always restored, including when the request fails.

**Input validation happens twice.** Empty fields are caught in the browser before any
request is sent, and the server independently rejects missing or non-numeric
parameters. Client-side checks are a convenience, not a guarantee: a request can reach
the server without passing through the page at all, so the service validates on its
own.

![Invalid input producing a controlled 400 and a readable message](docs/async-client-error.png)

Values are URL-encoded before being placed in the query string, which is the
counterpart of the decoding performed by the server. Responses are written into the
page as text rather than as markup, so a value coming back from the server is never
interpreted as HTML.

---

### 6.2 Observing the sequential limitation

The prediction recorded in section 1 was that a second user would wait while the
first one runs a slow request. The experiment confirms it.

A deliberately slow service was added for this purpose. One window requested it; two
seconds later, a second window requested the server time — an operation that normally
completes in about six milliseconds.

| Window | Request | Time |
|---|---|---|
| 1 | `/app/slow?seconds=5` | 5.03 s |
| 2 | `/app/time` | 3.09 s |

While the slow request was in flight, the second window's request sat in `(pending)`
state. It completed only once the first one finished, and the timestamps show how
tightly the two are coupled:

| Response | Timestamp |
|---|---|
| Slow request finished | `2026-09-07T21:17:27.2167702-05:00` |
| Server time answered | `2026-09-07T21:17:27.2247599-05:00` |

Eight milliseconds apart. The second window was not waiting for the clock to be read;
it was waiting for its turn.

![Second window pending while the slow request runs](docs/sequential-pending.png)

![Second window resolved after 3.09 s](docs/sequential-waited.png)

![Slow request in the first window](docs/sequential-slow.png)

**Asynchronous client, non-concurrent server.** These are two different properties and
they are easy to confuse. The second window never froze: its page stayed interactive
and its buttons reacted, because `await` does not block the browser. But the server is
a single thread inside a loop, and that thread was held by the first request. It does
not return to accepting connections until the current response is written. The second
connection had already been established by the operating system and was sitting in the
socket's accept queue with nobody to read it.

Asynchronous JavaScript improves what the user sees while waiting. It does not change
how many requests the server can handle at once.

---

### 7. Deployment

#### 7.1 Preparing the application

The build produces a single executable artifact under `target/`. The public
resources live in `src/main/resources`, so the HTML, the stylesheet, the client
script and both images are packaged inside the jar: deployment is one file to
transfer, with nothing to keep in sync alongside it.


**Configurable port.** The listening port is taken from the first command-line
argument, then from the `PORT` environment variable, and falls back to 35000 if
neither is set. Running the same artifact twice on different ports confirms it:

![Two instances started on different ports](docs/configurable-port.png)

Both instances serve the full application, images and stylesheet included, which
verifies that the resources are read from inside the jar rather than from the
project directory:

![The same application served on ports 8080 and 35000](docs/jar-two-ports.png)

**Remote connections.** The server socket is created without binding to a specific
address, so it listens on all interfaces rather than only on the loopback address.
This is what allows the application to be reached from outside once it runs remotely.

**Runtime required:** Java 21. The packaged artifact was tested locally before being
uploaded.

#### 7.2 Security group

The instance firewall exposes only what the laboratory needs: the application port,
and administrative access restricted to a single address.

| Type | Port | Source | Purpose |
|---|---|---|---|
| Custom TCP | 35000 | Anywhere-IPv4 | Application traffic for the classroom test |
| SSH | 22 | My IP only | Administrative access |

SSH is deliberately not open to every address: port 35000 serves a public web page,
while port 22 grants administrative access to the machine.

![Inbound rules](docs/security-group.png)

#### 7.3 Install and start

The Java runtime was installed on the instance with the distribution's package
manager:

![Java 21 installed on the instance](docs/ec2-java.png)

The artifact was transferred over SSH with `scp`, and the health service was verified
from **inside** the instance before testing from the browser. Checking locally first
separates an application problem from a network problem:

![Health service answering from inside the instance](docs/ec2-health.png)

The application then answered on the instance's public address. All five static
resources and all four services respond correctly:

![The application running from the EC2 public address](docs/ec2-running.png)

**What changed and what did not.** Response times went from tens of milliseconds
locally to hundreds remotely — the cost of the round trip between the client and the
region, not of the server itself. The slow service still takes just over five seconds
and still blocks every other request while it runs. The host changed; the
architecture did not.

#### 7.4 Running after logout

The application is managed by the operating system's service manager rather than
started by hand. The unit sets the port through the `PORT` environment variable,
restarts the process if it fails, and appends output to a known log file.

![Service reported as active and enabled](docs/ec2-systemd.png)

This was verified rather than assumed: the SSH session was closed and the application
kept answering.

![The page still responding after the administration session was closed](docs/ec2-logout.png)

Logs are written to a fixed location and can be inspected at any time:

![Server log on the instance](docs/ec2-logs.png)

---

### 8. Final validation and reflection

**Pending.** Acceptance checklist and answers to the eight discussion questions.

---

---

### 9. Deliverables

#### 9.1 Repository

Public GitHub repository using Maven as its build and dependency-management tool, with
the conventional source layout and small, meaningful commits showing the evolution from
the minimal server to the deployed application.

```
.
├── pom.xml
├── README.md
├── .gitignore
├── docs/                            # evidence screenshots
└── src
    ├── main
    │   ├── java/co/edu/escuelaing/httpserver/httpserver/
    │   │   ├── HttpServer.java      # connection loop, request parsing, routing
    │   │   ├── Response.java        # status, content type and body as bytes
    │   │   ├── Services.java        # the hardcoded services
    │   │   ├── EchoServer.java      # socket exercise from part 1
    │   │   ├── EchoClient.java      # socket exercise from part 1
    │   │   ├── ReadURL.java         # URL inspection exercise
    │   │   └── URLReader.java       # reading a page and its response headers
    │   └── resources/public/        # HTML, CSS, JavaScript and images
    └── test/java/co/edu/escuelaing/httpserver/httpserver/
        ├── HttpServerTest.java
        └── ServicesTest.java
```

Application code, tests and public resources are kept in separate trees. Nothing under
`target/` is committed: the artifact is rebuilt from source.

#### 9.2 Automated tests

Tests live under `src/test/java`, separate from the application source, and run as part
of the build:

```
mvn test
```

None of them opens a socket. They exercise the decisions the server makes — which content
type is assigned, which paths are rejected, how the query string is split, what each
service returns for valid and invalid input — without starting the server, so they run in
milliseconds and do not depend on a free port.

| Class | Covers |
|---|---|
| `HttpServerTest` | Content type selection, path safety, query string parsing, 200/403/404 responses |
| `ServicesTest` | Greeting, square, health, JSON escaping, and the client-error path of each service |

This is what separating the services from the networking code buys: if every response
were written straight to the socket, none of it could be verified without a running
server.

![15 tests passing](docs/tests.png)

#### 9.3 .gitignore

The repository excludes Maven build output, compiled class files, IDE configuration
(both NetBeans and VS Code, since the project moved between them), operating-system
metadata, logs, environment files, private keys and local cloud configuration.

Two entries matter most here. `target/` holds the compiled classes and the jar, all of
which are regenerated by `mvn package` and have no reason to travel in the repository.
`*.pem` is the extension of the private key used to reach the instance over SSH: a key
committed to a public repository gives anyone who clones it administrative access to the
machine.

The history was inspected before submission to confirm that none of these — and no
secret, token or credential — was ever committed:

```
git ls-files
```

Adding a secret to `.gitignore` after committing it does not remove it from the
history, so the check was run against the tracked files rather than the working
directory.

---

## Prerequisites

- Java 21 (JDK)
- Apache Maven 3.9 or later
- A web browser with developer tools

```bash
java -version
mvn -version
```

---

## Preliminary exercises from part 1

| Class | What it does | Evidence |
|---|---|---|
| `ReadURL` | Prints protocol, authority, host, port, path, query and file of a URL | `docs/…` |
| `URLReader` | Reads a remote page and prints its HTTP response headers | `docs/…` |
| `EchoServer` / `EchoClient` | Echo protocol over raw TCP sockets | `docs/…` |

```bash
java -cp target/classes co.edu.escuelaing.httpserver.httpserver.ReadURL
java -cp target/classes co.edu.escuelaing.httpserver.httpserver.URLReader
java -cp target/classes co.edu.escuelaing.httpserver.httpserver.EchoServer
java -cp target/classes co.edu.escuelaing.httpserver.httpserver.EchoClient
```

---

## Known limitations

- The server is **sequential**: one connection at a time. A slow request blocks every
  other client.
- The HTML page is embedded in the Java source instead of being served from a file.
- Every response is announced as HTML, including the JSON one.
- No content length is sent.
- No static resources, images or external scripts are served.
- Only one dynamic path is recognised, and it returns the raw query string rather than a
  parsed parameter.
- Unsupported methods and missing resources are not distinguished from a normal response.
- This is a teaching exercise, **not a production-ready HTTP server**.

---
