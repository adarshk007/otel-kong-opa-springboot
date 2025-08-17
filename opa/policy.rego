package http.authz

default allow = false

# Allow public endpoint
allow {
  input.method == "GET"
  startswith(input.path, "/api/hello")
}

# Allow if X-User header is present for /api/secure
allow {
  startswith(input.path, "/api/secure")
  some user
  user := input.headers["x-user"]
  user != ""
}
