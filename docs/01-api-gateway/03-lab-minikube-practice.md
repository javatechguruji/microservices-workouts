# Lab — Stage 3 Hands-On: Kubernetes Routing & Service Discovery on Minikube

> **Companion to:** `03-kubernetes-routing-service-discovery.md`
> **Goal:** Actually run the concepts from that doc against *this* repo, on *your* Minikube, with real commands and real output — not the generic `payment-service` / port-8080 example used in the reference doc.
> **You do not need to read the reference doc first**, but it explains the *why* behind every step here in more depth.

---

## 0. How this lab differs from the reference doc

The reference doc (`03-kubernetes-routing-service-discovery.md`) uses a simplified, hypothetical layout to teach the concepts: every service on port `8080`, a `payment-service`, and a flat `/api/orders/{id}` endpoint.

Your actual repo is slightly different. This lab uses the **real** values so every command below actually works.

| Reference doc | This repo (actual) |
|---|---|
| `order-service` on `8080` | `order-service` on **`9091`** |
| `payment-service` | **does not exist** — repo has `product-service` (`8081`) and `inventory-service` (`8082`) instead |
| Gateway on `8000` | matches — gateway-service is on **`8000`** |
| `GET /api/orders/101` | Gateway rewrites to `/orders/v1/{id}` or `/orders/v2/{id}` depending on the `X-API-Version` header (see `gateway-service/src/main/resources/application.yml`) |
| `application-local.yml` / `application-k8s.yml` don't exist yet | **already exist** in `gateway-service/src/main/resources/` — Stage 3's profile split (doc section 41-42) is already done for you |
| Every service has a Dockerfile | only `gateway-service` and `order-service` have one; `product-service` and `inventory-service` don't yet, and have no REST controller either |

**Consequence for this lab:**

- **Part A** (the core lab) deploys `gateway-service` + `order-service` — the two services that are actually wired end-to-end today. This is a complete, faithful run of every concept in the reference doc.
- **Part B** is optional stretch work to bring `product-service` and `inventory-service` into the cluster too. They need a Dockerfile and a minimal controller first, since neither exists yet — templates are provided.

---

## 1. Environment Check — Do You Have What You Need?

Run every command in this lab from the **repo root** (`microservices-workouts/`) — all paths (`k8s/...`, `./order-service`, etc.) are relative to it.

Run these first. They are all read-only / informational.

```bash
# Is Docker installed and running?
docker --version
docker info >/dev/null 2>&1 && echo "Docker daemon is running" || echo "Docker daemon is NOT running — start Docker Desktop"

# Is Minikube installed?
minikube version

# Is kubectl installed?
kubectl version --client
```

On this machine, all three are already installed:

```text
minikube v1.35.0
Docker version 29.5.3
kubectl client v1.23.5   <- older than the minikube v1.32 server; usually fine, but see note below
```

If any of these print "command not found":

```bash
# macOS, via Homebrew
brew install --cask docker      # Docker Desktop
brew install minikube
brew install kubectl
```

> **kubectl client/server version skew:** your `kubectl` client (v1.23) is a few minor versions behind the Minikube-managed Kubernetes server (v1.32). Kubernetes generally supports clients within ±1 minor version of the server; a bigger skew like this usually still works for `get`/`apply`/`describe`/`logs`, but if you hit weird `kubectl` errors later, that's the first thing to suspect. Fix with `brew upgrade kubectl`.

### Check whether Minikube already has a cluster

```bash
minikube status
```

Possible outputs and what they mean:

```text
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```
→ Cluster is healthy, skip to section 2's verification step.

```text
host: Running
kubelet: Stopped
apiserver: Stopped
kubeconfig: Configured
```
→ The VM exists but Kubernetes itself isn't up. Run `minikube start` (see below) to bring it fully online.

```text
Nonexistent / minikube not found
```
→ No cluster yet at all. Go to section 2.

---

## 2. Minikube Setup

### 2.1 Start (or resume) the cluster

```bash
minikube start
```

Useful variants if you want more resources for running 2+ services:

```bash
minikube start --cpus=4 --memory=6144
```

If you already have a cluster in a half-started state (host running, apiserver stopped, as diagnosed above), the same `minikube start` command will repair/resume it — it's idempotent, safe to re-run.

### 2.2 Verify the cluster is healthy

```bash
minikube status
kubectl get nodes
kubectl cluster-info
```

Expect a single `Ready` node named `minikube`.

### 2.3 Confirm kubectl is pointed at Minikube (not some other cluster)

```bash
kubectl config current-context
```

Should print `minikube`. If it prints something else:

```bash
kubectl config use-context minikube
```

### 2.4 Handy Minikube commands you'll use throughout this lab

```bash
minikube dashboard              # opens a web UI for the whole cluster
minikube ip                     # the VM's IP address
minikube service <name> -n <ns> --url   # get a reachable URL for a NodePort service
minikube image ls               # list images already loaded into the cluster
minikube image build -t <tag> <context-dir>   # build an image directly into the cluster
minikube pause / unpause        # freeze/resume without deleting state
minikube stop                   # stop the cluster, keep it on disk (fast to resume)
minikube delete                 # fully delete the cluster (use if things get into a bad state)
```

> **Note on this cluster's driver:** `minikube profile list` shows this cluster uses the `docker` driver with the `containerd` runtime. That means `eval $(minikube docker-env)` + plain `docker build` is flagged by Minikube itself as "highly experimental" on this setup. **Use `minikube image build` instead** throughout this lab — it works regardless of driver/runtime and is what the reference doc uses too.

---

## 3. Namespace

Create the namespace all Stage 3 resources live in.

`k8s/namespace.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce
```

### 3.1 What a Namespace Actually Does (Field-by-Field)

This is the simplest manifest in the whole lab — only three fields — but it's worth understanding what each buys you, and why every other manifest in this lab (`order-service.yaml`, `gateway-service.yaml`, ...) references it.

| Field | What it actually does |
|---|---|
| `apiVersion: v1` | Namespace is a **core** Kubernetes API object (unlike `Deployment`, which lives under `apps/v1`) — it's been stable since Kubernetes 1.0, hence the plain `v1`. |
| `kind: Namespace` | Declares this object as a **virtual cluster-within-a-cluster** — a scoping boundary for names, not a physical resource like a Pod or Node. |
| `metadata.name: ecommerce` | The name every other manifest's `metadata.namespace: ecommerce` field references. This is the string that ties `order-service.yaml`, `gateway-service.yaml`, and everything else in this lab together into one logical group. |

**Why bother with a Namespace at all?** Three concrete reasons, all of which matter later in this lab:

1. **Name isolation** — you could have an `order-service` in `ecommerce` and a completely unrelated `order-service` in another namespace (e.g. `staging`) without collision. Every object's *true* identity is `name + namespace`, not just `name`.
2. **Blast radius for cleanup** — section 27's `kubectl delete namespace ecommerce` deletes *everything* created in this lab (Deployments, Services, Secrets, Certificates from Part C) in one command, without touching anything else running in Minikube (like `cert-manager`, which deliberately installs into its own `cert-manager` namespace, not `ecommerce`).
3. **A natural unit for RBAC/quotas in real clusters** — not exercised in this lab, but in production, namespaces are typically where you'd attach `ResourceQuota`s (CPU/memory limits) and `RoleBinding`s (who can touch what) per team or environment.

**What happens if you skip this and just `kubectl apply` the other files without a namespace?** Every object would land in the `default` namespace instead. It would still technically work — Service DNS resolution, selectors, etc. all function the same — but you'd lose the isolation/cleanup benefits above, and every `kubectl get pods` (without `-n`) would mix this lab's Pods in with anything else you've ever run in `default`.

### 3.2 Apply & Verify (Do This Now)

**Step 1 — apply the manifest:**

```bash
kubectl apply -f k8s/namespace.yaml
```
*Sends the YAML file to the Kubernetes API server, which creates the Namespace object. `apply` is declarative — safe to re-run any time, it only changes what's different from the current state.*

**Step 2 — verify it was created:**

```bash
kubectl get namespaces
```
*Lists every namespace the cluster knows about, so you can confirm `ecommerce` actually exists — not just that the previous command didn't error.*

You should see `ecommerce` in the list, with `STATUS: Active`.

**Step 3 — inspect it (optional, but useful to see what "empty" looks like before you start adding resources):**

```bash
kubectl describe namespace ecommerce
```
*Prints full details about that one object — status, labels, any resource quotas — useful for catching anything unexpected attached to it.*

```bash
kubectl get all -n ecommerce
```
*Lists every common resource type (Pods, Services, Deployments...) scoped to this namespace — a way to see it's genuinely empty before you start adding things.*

`get all` should return `No resources found in ecommerce namespace.` at this point — that's expected; you haven't applied `order-service.yaml` or `gateway-service.yaml` yet.

**Step 4 — (optional) set it as your default namespace**, so you don't have to type `-n ecommerce` on every command for the rest of this lab:

```bash
kubectl config set-context --current --namespace=ecommerce
```
*Edits your local kubeconfig so `kubectl` defaults to this namespace when you omit `-n` — a convenience setting, not a cluster change.*

Verify it took effect:

```bash
kubectl config view --minify | grep namespace:
```
*Prints your current context's config and filters for the `namespace:` line, confirming the previous command actually took effect.*

(All commands elsewhere in this doc still show `-n ecommerce` explicitly so they work regardless of whether you did this step.)

---

## Part A — Core Lab: `gateway-service` + `order-service`

This is the part of the reference doc that maps 1:1 to code that already exists and already runs in this repo.

### 4. Order Service — Deployment + Service

Order Service listens on **`9091`** (see `order-service/src/main/resources/application.yml`), not `8080` as in the reference doc's generic example.

`k8s/order-service.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ecommerce
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: order-service:stage3
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 9091
          env:
            - name: POD_IP
              valueFrom:
                fieldRef:
                  fieldPath: status.podIP
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ecommerce
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
    - name: http
      port: 9091
      targetPort: 9091
```

This matches the naming convention already declared in `k8s/README.md` — one file per service, Deployment + Service combined.

### 4.1 Understanding Every Field (Nothing Here Is Actually Redundant)

The YAML above repeats `order-service` and `app: order-service` a lot, and `9091` shows up twice. It looks redundant, but almost every repetition is a pointer between **independent objects that only find each other by label matching** — not copy-paste noise.

**In the Deployment:**

| Field | What it actually does |
|---|---|
| `metadata.name: order-service` | Name of the **Deployment object** itself. Used for `kubectl get deployment order-service`. Has no functional link to anything below. |
| `spec.selector.matchLabels.app: order-service` | Tells the Deployment (via its ReplicaSet) **which Pods belong to it** — "manage any Pod carrying label `app: order-service`." |
| `spec.template.metadata.labels.app: order-service` | The label **stamped onto each Pod** this Deployment creates. |
| `spec.template.spec.containers[].name: order-service` | Name of the **container inside the Pod** (for multi-container pods — used by `kubectl logs -c order-service`, `kubectl exec -c order-service`). Unrelated to the labels above. |
| `containers[].env[].valueFrom.fieldRef.fieldPath: status.podIP` | This is the **Kubernetes Downward API** — a mechanism for injecting facts about the Pod's *own runtime identity* (its IP, name, namespace, labels, resource limits, ...) into an env var, without the app needing to call the Kubernetes API itself. `HOSTNAME` (used by `servedBy()`) is set automatically by the container runtime to the Pod's name; `POD_IP` is *not* automatic — it has to be explicitly requested this way, because a Pod's IP isn't known until the scheduler actually places it on a node. The app reads it via `System.getenv("POD_IP")` — see `OrderController.podIp()`. |

**The one duplication that's actually mandatory:** `selector.matchLabels` must equal `template.metadata.labels` — the Kubernetes API rejects a Deployment otherwise. That's not sloppy YAML, that's the mechanism: the selector is the *query*, the template label is the *thing the query matches*. They have to agree or the Deployment can't find its own Pods.

**In the Service:**

| Field | What it actually does |
|---|---|
| `metadata.name: order-service` | Becomes the **DNS name** other Pods use (`order-service`, or fully `order-service.ecommerce.svc.cluster.local`). This is the one that matters for service discovery. |
| `spec.selector.app: order-service` | The Service's **own independent query** — "send traffic to any Pod with label `app: order-service`." |

The Service has no idea the Deployment exists — it never references it by name. It only looks at Pod labels directly. That's deliberate: it decouples "who runs the Pods" (Deployment) from "who routes to them" (Service). You could delete the Deployment, hand-create bare Pods with `app: order-service`, and the Service would route to them just the same.

So `app: order-service` shows up three times (Deployment selector, Pod template label, Service selector) because **three independent things each need to agree on the same tag** to stay wired together. That tag value is a convention *you* chose (using the service name as the label), not a Kubernetes requirement — `app: banana` would work identically everywhere, consistently. The *value* is arbitrary; the *matching* across objects is what's required.

**Why `port` appears in both places:**

These are two genuinely different numbers that happen to be equal here:

- **`containerPort: 9091` (Deployment)** — documents which port the container process listens on inside the Pod. Mostly informational/for tooling (probes, `port-forward`); the app would listen there even if this line were omitted.
- **Service `port: 9091`** — the port *callers* use to reach the Service (`order-service:9091`).
- **Service `targetPort: 9091`** — the port traffic gets forwarded to *on the Pod* once it arrives.

`port` and `targetPort` are independently configurable — a Service could expose `port: 80` while forwarding to `targetPort: 9091` on the Pod. Section 14.3 later in this lab proves this by deliberately breaking `targetPort` to `9999` while leaving `port: 9091` untouched — `kubectl get svc` still looks fine, but every call gets connection-refused, because the Service is now forwarding to a port nothing listens on. That failure mode only makes sense once `port` and `targetPort` are understood as separate knobs, not one redundant value.

> **Why no port `80` anywhere?** Port 80/443 only matters at the one hop a browser/client actually connects to with no port typed in the URL — a public Load Balancer or Ingress. `order-service` is `ClusterIP` (internal-only, no external caller ever types a bare URL for it), and even `gateway-service` — the one component that *could* be a public front door — uses `8000`/`8443` here, not 80, because every internal caller has the exact `host:port` wired into its config (`services.order.url`, `SERVICES_ORDER_URL`) rather than guessing a default. In a real EKS deployment, port 80/443 would live at exactly one place: the ALB/NLB listener that Route 53 points at — it terminates 443 and forwards to whatever `targetPort` the Gateway Service actually runs, which the Gateway Pod itself never needs to know or care is "the standard port."

### 4.2 `ClusterIP` vs. `NodePort` (Why `order-service` Is `ClusterIP`)

`spec.type` on a Service controls **who is allowed to reach it** — it doesn't change routing/selector behavior at all, only reachability.

| Type | Reachable from | Gets an external IP/port? | Typical use |
|---|---|---|---|
| `ClusterIP` (default) | Only from **inside** the cluster (other Pods) | No | Internal-only services — databases, backend microservices, anything that should never be hit directly from outside |
| `NodePort` | From inside the cluster, **and** from outside via `<any-node-IP>:<a-30000-32767-port>` | Yes — a high, auto/manually-assigned port on every node | Quick/dev-only external access; rarely used directly in production |
| `LoadBalancer` | From the public internet, via a real cloud load balancer | Yes — a proper external IP (on Minikube, emulated by `minikube tunnel`) | Production public-facing entry points |

**Why `order-service` is `ClusterIP`:** nothing outside the cluster should ever call `order-service` directly. The only caller is `gateway-service`, which runs *inside* the same cluster — so a `ClusterIP` (internal-only, stable DNS name `order-service`, no external exposure at all) is exactly the right, most-locked-down choice. This is also why section 10 has you prove DNS resolution from a *disposable Pod inside the cluster* (`kubectl run curl-test ...`), not from your Mac's terminal — a `ClusterIP` Service isn't reachable from your Mac by design.

**Why `gateway-service` is `NodePort` (Part A) instead:** it's the one service in this lab that a human (you, on your Mac, outside the cluster) needs to actually `curl`. `NodePort` is the quick way to get that in Minikube without a real cloud load balancer — `minikube service gateway-service -n ecommerce --url` gives you a local tunnel to it. In a real production cluster you would *not* use `NodePort` for this — you'd use `LoadBalancer` (or an Ingress) instead, which is exactly what Part C of this lab switches `gateway-service` to once TLS termination is introduced.

**Rule of thumb going forward:** every internal-only microservice (`order-service`, `product-service`, `inventory-service`) should be `ClusterIP`. Only the single edge component that the outside world actually calls (`gateway-service`) should ever be `NodePort`/`LoadBalancer`.

**So who is actually load-balancing across `order-service`'s two Pods?** None of the three `spec.type` values above do that themselves — `spec.type` only controls *reachability* (as stated at the top of this section). The actual load-balancing across replicas is done by **`kube-proxy`** — a component that runs on every node in *every* Kubernetes cluster automatically (`kubectl get pods -n kube-system -l k8s-app=kube-proxy` shows it; nothing to install, nothing to configure). It watches the API server for `Service`/`Endpoints` changes and programs **iptables rules** directly into the node's kernel. When any Pod opens a connection to `order-service`'s `ClusterIP`, those kernel-level rules rewrite the destination to one of the real Pod IPs — picked fresh, essentially at random, **per new TCP connection**, not per HTTP request. That per-connection (not per-request) detail is exactly why section 11 calls out that repeated calls don't alternate cleanly when a client (or the Gateway's own HTTP client pool) reuses one open connection for many requests. This mechanism is identical regardless of whether the Service is `ClusterIP` or `NodePort` — it's what happens *after* traffic already got past the reachability layer.

### 4.3 Build & Apply `order-service` (Do This Now)

`k8s/order-service.yaml` already exists in the repo. Everything below is scoped to just `order-service` — `gateway-service` isn't built/applied yet (that happens in section 5-7), so don't expect the Gateway to respond to anything yet.

**Step 1 — build the image directly into Minikube:**

```bash
minikube image build -t order-service:stage3 ./order-service
```
*Builds a Docker image from the Dockerfile in `./order-service`, tags it `order-service:stage3`, and loads it directly into Minikube's internal image store — skipping any external registry (Docker Hub, ECR, etc.) entirely.*

Verify the image landed:

```bash
minikube image ls | grep order-service
```
*Lists every image Minikube currently has cached and filters for `order-service`, confirming the build actually landed where the cluster can see it — not just that the build command exited 0.*

**Step 2 — apply the namespace (if you haven't already) and the manifest:**

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/order-service.yaml
```
*Sends both manifests to the API server. The first creates the `ecommerce` namespace (see section 3); the second creates the Deployment + Service, which is what actually schedules Pods onto the cluster.*

**Step 3 — verify the Deployment and Pods:**

```bash
kubectl get deployment order-service -n ecommerce
```
*Shows the Deployment's rollout status (e.g. `2/2` ready) — confirms Kubernetes successfully scheduled and started your Pods, not just that the object was created.*

```bash
kubectl get pods -n ecommerce -l app=order-service -o wide
```
*Lists only the Pods carrying that label; `-o wide` adds extra columns (Pod IP, Node) — the fastest way to see if they're `Running` or stuck in a bad state.*

Expect `2/2` ready on the Deployment, and two Pods in `Running` state. If you see `ImagePullBackOff`/`ErrImagePull`, the image tag in step 1 doesn't match `image: order-service:stage3` in the YAML exactly — recheck both.

**Step 4 — verify the Service and its wiring to Pods:**

```bash
kubectl get service order-service -n ecommerce
```
*Shows the Service object itself — its type (`ClusterIP`), assigned internal IP, and port — confirms it exists as expected.*

```bash
kubectl get endpoints order-service -n ecommerce
```
*Shows which Pod IPs the Service is actually routing to right now — the real proof the selector/label match is working, not just that the Service "exists."*

`get endpoints` should list two `IP:9091` pairs — one per replica. If it shows `<none>`, the Service's `selector.app` doesn't match the Pods' `labels.app` (see section 4.1/14.2 for why that breaks things).

**Step 5 — confirm it's reachable from inside the cluster (it's `ClusterIP`, so this is the only way — see section 4.2):**

```bash
kubectl run curl-test --rm -it -n ecommerce --image=curlimages/curl -- curl http://order-service:9091/orders/v1/1
```
*Spins up a temporary Pod (auto-deleted via `--rm` when you exit) that runs `curl` against `order-service` by its DNS name — the only way to test a `ClusterIP` Service, since it isn't reachable from your Mac's terminal by design.*

You should get back the same JSON shape used later in section 9 (`orderId`, `customerId`, `amount`, `status`, `apiVersion`, `servedBy`, `podIp`). Once this works, `order-service` is fully proven end-to-end and you're ready to move on to section 5 (`gateway-service`).

### 5. Gateway Service — Deployment + Service

Gateway listens on **`8000`**. Its Kubernetes profile file already exists at `gateway-service/src/main/resources/application-k8s.yml` and already points at `http://order-service:9091` — you don't need to change any application code, only activate the profile via an env var.

`k8s/gateway-service.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-service
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway-service
  template:
    metadata:
      labels:
        app: gateway-service
    spec:
      containers:
        - name: gateway-service
          image: gateway-service:stage3
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8000
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: k8s
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: ecommerce
spec:
  type: NodePort
  selector:
    app: gateway-service
  ports:
    - name: http
      port: 8000
      targetPort: 8000
```

`NodePort` here (vs. `ClusterIP` for order-service) is deliberate — it's the one service you need reachable from your Mac during this lab.

### 5.1 What's Different From `order-service` (Field-by-Field)

Most fields here follow the exact same pattern explained in section 4.1 (`metadata.name` ↔ `selector.matchLabels` ↔ `template.labels` ↔ Service `selector` all tied together by the `app: gateway-service` label). Only the genuinely new/different pieces are called out below.

| Field | What's different vs. `order-service`, and why |
|---|---|
| `spec.replicas: 1` (was `2`) | The Gateway is a single instance in this lab — fine for dev/learning. In real production you'd run 2+ replicas here too (that's Stage 4, section 29), but nothing in Part A/B requires it. |
| `containers[].ports[].containerPort: 8000` (was `9091`) | Just the port this particular app happens to listen on — Spring Cloud Gateway's configured port, set in `gateway-service/src/main/resources/application.yml`. |
| `containers[].env` (new — `order-service` doesn't have this) | `SPRING_PROFILES_ACTIVE=k8s` tells Spring Boot to load `application-k8s.yml` **on top of** the base `application.yml`, overriding `services.order.url` to `http://order-service:9091` (the in-cluster DNS name) instead of the `local` profile's `http://localhost:9091`. This is the *only* difference between running this JAR on your laptop vs. inside Minikube — see section 16. |
| `spec.type: NodePort` (Service) (was `ClusterIP`) | Covered in section 4.2 — this is the one Service in Part A a human needs to reach from outside the cluster, so it can't be `ClusterIP`. |

**Why is the profile named `k8s`? Is that a reserved/special name?**

No — `k8s` has **zero special meaning to Spring Boot**. A Spring profile is just an arbitrary string; you could name it `banana` and it would work identically, as long as every place that references it (the `SPRING_PROFILES_ACTIVE` env var here, and the `application-<profile>.yml` filename) uses the same string. `k8s` was chosen purely for human readability — it's short for "this is the config to use when running inside Kubernetes," mirroring the actual filename `application-k8s.yml`. The pairing you'll see in `gateway-service/src/main/resources/`:

| File | Loaded when | `services.order.url` value |
|---|---|---|
| `application.yml` | always (base config) | not set here — `spring.profiles.active: local` default lives here instead |
| `application-local.yml` | `SPRING_PROFILES_ACTIVE=local` (or unset — `local` is the default in `application.yml`) | `http://localhost:9091` |
| `application-k8s.yml` | `SPRING_PROFILES_ACTIVE=k8s` (set by `k8s/gateway-service.yaml`'s `env` block) | `http://order-service:9091` |

**Why doesn't `order-service` need any of this?** Look at `order-service/src/main/resources/` — there's only one file, `application.yml`, no `-local`/`-k8s` split at all. That's not an oversight; it's because **`order-service` doesn't call any other service whose address changes between your laptop and Minikube** in Part A of this lab. It's a leaf/backend service — things call *it*, it doesn't need to resolve anyone else's hostname. (It does have hardcoded `services.product-service-url`/`inventory-service-url` entries pointing at `localhost`, but nothing in Part A's controller code actually calls those yet — that only becomes relevant if you do the Part B stretch work.)

**The general rule this illustrates:** a Spring profile split (and the `SPRING_PROFILES_ACTIVE` env var to select one) is only needed by a service that has **environment-dependent configuration** — most commonly, a hardcoded hostname/URL to another service that differs between `localhost` (your machine) and a Kubernetes DNS name (`some-service:port`). `gateway-service` needs it because `services.order.url` is exactly that kind of value. If you bring `product-service`/`inventory-service` into the cluster later (Part B) and have `order-service` actually call them, `order-service` would need this same `local`/`k8s` split at that point too.

### 5.2 Build & Apply `gateway-service` (Do This Now)

`order-service` should already be running from section 4.3. This wires up the Gateway in front of it.

**Step 1 — build the image directly into Minikube:**

```bash
minikube image build -t gateway-service:stage3 ./gateway-service
```
*Same idea as order-service's build in 4.3 — builds the Gateway's Dockerfile into an image tagged `gateway-service:stage3` and loads it straight into Minikube's image store.*

Verify:

```bash
minikube image ls | grep gateway-service
```
*Confirms that image is now visible to the cluster, same purpose as the equivalent check in 4.3.*

**Step 2 — apply the manifest:**

```bash
kubectl apply -f k8s/gateway-service.yaml
```
*Sends the Gateway's Deployment + Service to the API server, creating the Pod and the NodePort Service in front of it.*

**Step 3 — verify the Deployment and Pod:**

```bash
kubectl get deployment gateway-service -n ecommerce
```
*Shows rollout status — expect `1/1` since this Deployment has only one replica.*

```bash
kubectl get pods -n ecommerce -l app=gateway-service -o wide
```
*Lists the Gateway's own Pod(s) and their state/IP/node — same purpose as the order-service check in 4.3.*

Expect `1/1` ready. If it's stuck `ImagePullBackOff`, recheck the tag from step 1 against `image: gateway-service:stage3` in the YAML. If it's `Running` but crash-looping, check logs (step 5 below) — a common cause at this stage is the `k8s` profile failing to resolve `order-service`, which usually means section 4 wasn't applied yet.

**Step 4 — verify the Service:**

```bash
kubectl get service gateway-service -n ecommerce
```
*Shows the Service's type/IP/port — here you'll see `NodePort` and a randomly assigned high port, unlike order-service's plain `ClusterIP`.*

Note the `NodePort` column — it's a random port in the `30000-32767` range that Kubernetes assigned. You generally don't call that port directly; instead:

**Step 5 — get a reachable URL and smoke-test it:**

```bash
minikube service gateway-service -n ecommerce --url
```
*Opens a local tunnel from your Mac into the NodePort Service and prints a real `http://127.0.0.1:<port>` URL you can `curl` — Minikube's workaround, since the NodePort itself lives inside a Docker-driver VM that isn't directly reachable from your host.*

This blocks in the foreground on some driver/OS combos — if so, open a second terminal, or append `&` to background it. It prints something like `http://127.0.0.1:54321`. Keep that URL handy, then:

```bash
curl -s http://127.0.0.1:54321/api/orders/1 | jq .
```
*Sends a real HTTP request through the entire chain — tunnel → Gateway Pod → `order-service` DNS → Order Pod — and pretty-prints the JSON with `jq`. No header, so the Gateway's default route (`v1`) handles it.*

```bash
curl -s -H "X-API-Version: 2" http://127.0.0.1:54321/api/orders/1 | jq .
```
*Same call, but the added header makes the Gateway's `Header=X-API-Version, 2` predicate match instead, routing to the `v2` path.*

The first call should come back with `"apiVersion": "v1"`, the second with `"apiVersion": "v2"` — proving the full chain (your Mac → NodePort tunnel → Gateway Pod → `order-service` DNS → Order Pod) end-to-end. If either call hangs or errors, use the troubleshooting cheat sheet in section 26 rather than guess-editing YAML.

**Step 6 — check logs if anything above misbehaved:**

```bash
kubectl logs -n ecommerce deployment/gateway-service --tail=50
```
*Prints the last 50 lines of the Gateway container's stdout/stderr — the first place to look for a stack trace or connection error when something above didn't behave as expected.*

Once both `curl`s above return the right `apiVersion`, Part A's core wiring (sections 4-9) is fully proven and you're ready for sections 10-13 (DNS proof, load distribution, pod-delete resilience, scaling).

### 5.3 Testing the Gateway via Postman Instead of `curl`

**Is `gateway-service` exposed to your Mac so Postman can hit it? Not automatically — `NodePort` alone is not enough on this setup.**

Here's why: this cluster uses the `docker` driver (per section 2.4's note), which means the entire Minikube "node" is itself a Docker container (you can see it: `docker ps` shows a container literally named `minikube`, and you confirmed this earlier by inspecting it directly). On Linux, a `NodePort` is normally reachable at `<minikube ip>:<nodeport>` directly. On **macOS**, that path doesn't work cleanly — Docker Desktop runs everything inside a hidden Linux VM, so the Minikube-node-container's IP isn't routable from your Mac's network stack at all. This is exactly the situation `minikube service --url` (step 5 above) exists to solve — it doesn't just print an address, it actively creates a real local TCP tunnel/port-forward from `127.0.0.1` on your Mac into the Service.

**The practical answer: whatever `127.0.0.1:<port>` URL `minikube service --url` gives you, that's a completely ordinary local address — Postman needs zero Kubernetes-specific configuration.** Point a Postman request at it exactly like you would any other local REST API.

**Option A — reuse the tunnel from step 5 (simplest):**

```bash
minikube service gateway-service -n ecommerce --url
```
*Same command as step 5 — must stay running in its own terminal for as long as you're testing in Postman, since it's an active tunnel process, not a one-time lookup.*

Copy the printed URL (e.g. `http://127.0.0.1:54321`) into Postman as your base URL, e.g. `http://127.0.0.1:54321/api/orders/1`, with header `X-API-Version: 2` added under the **Headers** tab to hit the `v2` route.

**Downside:** the port is randomly assigned and changes every time you re-run the command — you'd have to update the URL in Postman each session.

**Option B — `kubectl port-forward` (better for repeated Postman use, fixed port):**

```bash
kubectl port-forward svc/gateway-service -n ecommerce 8000:8000
```
*Opens a direct pipe from `localhost:8000` on your Mac straight to the Service's port `8000` — bypassing NodePort entirely (this works over the Kubernetes API server connection, which is always reachable, unlike raw node networking). Like the tunnel above, it blocks in the foreground and must keep running while you test — open a dedicated terminal for it.*

With this running, Postman requests go to a **stable** `http://localhost:8000/api/orders/1` every time — no re-copying random ports. This is generally the more convenient option once you're doing repeated manual testing rather than one-off `curl`s.

**Either way, in Postman:**
1. New request → `GET http://localhost:8000/api/orders/1` (or whichever URL/port you're using)
2. Send with no extra headers → expect `"apiVersion": "v1"` in the response body
3. Add header `X-API-Version: 2` → Send again → expect `"apiVersion": "v2"`
4. Send it 5-10 times in a row and watch the `podIp` field in the response change between two values — the same load-balancing proof as section 11, just clicked instead of scripted.

### 6. Build the Images Into Minikube

```bash
minikube image build -t order-service:stage3 ./order-service
minikube image build -t gateway-service:stage3 ./gateway-service

minikube image ls | grep -E "order-service|gateway-service"
```

Each build runs your existing multi-stage Dockerfile (Maven build → `eclipse-temurin:17-jre` runtime), so it can take a minute or two the first time.

> The image tag (`order-service:stage3`) must **exactly** match the `image:` field in the corresponding Deployment YAML above, or the Pod will sit in `ErrImagePull`/`ImagePullBackOff`.

### 7. Apply Everything

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/order-service.yaml
kubectl apply -f k8s/gateway-service.yaml
```

Verify:

```bash
kubectl get all -n ecommerce
```

Expect to see:

```text
pod/gateway-service-xxxxxxxxxx-xxxxx   1/1   Running
pod/order-service-xxxxxxxxxx-xxxxx     1/1   Running
pod/order-service-xxxxxxxxxx-yyyyy     1/1   Running

service/gateway-service   NodePort
service/order-service     ClusterIP

deployment.apps/gateway-service   1/1
deployment.apps/order-service     2/2
```

If a Pod is stuck in `ImagePullBackOff`, double-check the image name/tag matches step 6 exactly, and that `imagePullPolicy: IfNotPresent` is set (otherwise Kubernetes tries to pull from a registry instead of using the locally-built image).

### 8. Verify Service → Pod Wiring

```bash
kubectl get pods -n ecommerce -o wide
kubectl get service order-service -n ecommerce
kubectl get endpoints order-service -n ecommerce
kubectl get endpointslices -n ecommerce
```

`endpoints`/`endpointslices` for `order-service` should list two IP:port pairs (one per replica), each ending in `:9091`.

---

### 9. Test the Gateway From Your Mac

```bash
minikube service gateway-service -n ecommerce --url
```

This prints something like `http://127.0.0.1:54321` (a local tunnel to the NodePort). Keep that terminal/URL — reuse it for every `curl` below. If the command doesn't return (it can block on some driver/OS combos), open a second terminal to run it, or add `&` to background it.

**Default route (no header → v1):**

```bash
curl -s http://127.0.0.1:54321/api/orders/1 | jq .
```

Expected:

```json
{
  "orderId": 1,
  "customerId": "CUST-1001",
  "amount": 250.0,
  "status": "CONFIRMED",
  "apiVersion": "v1",
  "servedBy": "order-service-xxxxxxxxxx-xxxxx",
  "podIp": "10.244.0.7"
}
```

`podIp` is the actual Pod IP on the cluster's internal pod network (a `10.244.x.x`-style address by default in Minikube) — injected via the Kubernetes Downward API, not hardcoded. See section 4.1 for how, and section 11 for why this is the field that makes load balancing tangible.

**Header-based route to v2** (this exercises the `Header=X-API-Version, 2` predicate from your gateway's `application.yml`):

```bash
curl -s -H "X-API-Version: 2" http://127.0.0.1:54321/api/orders/1 | jq .
```

Expected: same shape, but `"apiVersion": "v2"`.

This proves Stage 1 (routing) + Stage 2 (predicates/filters) + Stage 3 (Kubernetes service discovery) are all working together, end to end, inside Minikube.

---

### 10. Prove Kubernetes DNS Directly (Don't Just Trust the Diagram)

Spin up a disposable Pod and call `order-service` by its DNS name, bypassing the gateway entirely:

```bash
kubectl run curl-test \
  --rm -it \
  -n ecommerce \
  --image=curlimages/curl \
  -- sh
```

Inside the shell:

```bash
curl http://order-service:9091/orders/v1/1
```

You should get the same JSON body directly from an Order Pod. Exit the shell (`exit`) — because of `--rm`, the Pod is deleted automatically.

---

### 11. Prove Pod Identity / Load Distribution

`OrderController` returns both the serving Pod's **name** (`servedBy`, via the `HOSTNAME` env var Kubernetes sets automatically) and its **actual IP address** (`podIp`, via the Downward API — see section 4.1). The IP is the more visceral one: a Pod *name* is just a string, but watching the literal IP address flip between requests is what makes "the Service is load-balancing across real, separate machines-in-miniature" click.

```bash
for i in $(seq 1 10); do
  curl -s http://127.0.0.1:54321/api/orders/1 | jq -r '"\(.servedBy)  \(.podIp)"'
done
```

You should see a mix of two distinct Pod names *and* two distinct IPs — not necessarily perfectly alternating (see reference doc section 29 for why: connection reuse means it isn't guaranteed round-robin at the request level). If you want to see it even more starkly, bypass the gateway/tunnel and hit the Service directly from inside the cluster (extends the DNS proof from section 10):

```bash
kubectl run curl-test --rm -it -n ecommerce --image=curlimages/curl -- sh -c \
  'for i in $(seq 1 10); do curl -s http://order-service:9091/orders/v1/1; echo; done' \
  | grep -o '"podIp":"[^"]*"'
```

**If you tested this and every call kept returning the *same* Pod through the Gateway/Postman, no matter how many times you retried:** that's expected, not a bug — and it's worth understanding exactly why, because it's a real production nuance, not a lab artifact.

The load balancer here is **`kube-proxy`** (see section 4.2) — it picks a destination Pod **once per new TCP connection**, via kernel-level iptables NAT, not once per HTTP request. Hitting `order-service` directly with separate `curl` processes (the command above) opens a fresh connection every time, so you see it alternate. But `curl`/Postman calls that go **through the Gateway** hit a different bottleneck: Spring Cloud Gateway's HTTP client (Reactor Netty) keeps a **connection pool** to `order-service` and reuses an already-open connection across many incoming requests, for performance. Whichever Pod that one pooled connection happened to land on is where *every* request riding it goes — until the pool opens a new connection (idle-timeout eviction, or genuine concurrency forcing a second connection).

You can force it to visibly split by sending several requests **concurrently** instead of one at a time:

```bash
for i in $(seq 1 8); do
  curl -s http://127.0.0.1:54321/api/orders/1 | grep -o '"podIp":"[^"]*"' &
done
wait
```

Concurrent requests can't all share one in-flight connection, so the pool opens more than one — and each of those gets its own independent `kube-proxy` pick. Section 12 (deleting a Pod) is another reliable way to force it: killing the Pod your pooled connection is pinned to breaks that connection outright, and the Gateway's next request is forced to open a fresh one.

### 12. Delete a Pod — Prove Gateway Config Never Changes

```bash
kubectl get pods -n ecommerce -l app=order-service -o wide
```

Pick one Pod name and delete it:

```bash
kubectl delete pod -n ecommerce <one-order-service-pod-name>
```

Watch it get replaced (Ctrl+C to stop watching):

```bash
kubectl get pods -n ecommerce -l app=order-service -w
```

Then re-run the loop from step 11 — the gateway's `uri: ${services.order.url}` never changed, yet traffic now reaches a brand new Pod with a different name/IP.

### 13. Scale Order Service

```bash
kubectl scale deployment order-service -n ecommerce --replicas=4
kubectl get pods -n ecommerce -l app=order-service
kubectl get endpoints order-service -n ecommerce
```

Scale back down:

```bash
kubectl scale deployment order-service -n ecommerce --replicas=2
```

At no point does `k8s/gateway-service.yaml` or the gateway's `application-k8s.yml` need to change — that's the entire point of Stage 3.

---

## 14. Deliberate Failure Exercises

Do these one at a time; revert each before starting the next so failures don't compound.

### 14.1 Wrong Service Name (DNS resolution failure)

Because the gateway route is templated as `uri: ${services.order.url}`, you can break it **without rebuilding the image**, using Spring Boot's env-var property override (`SERVICES_ORDER_URL` maps to `services.order.url`):

```bash
kubectl set env deployment/gateway-service -n ecommerce \
  SERVICES_ORDER_URL=http://orders-service:9091
```

This triggers a rolling restart of the gateway Pod. Once it's back up:

```bash
curl -s http://127.0.0.1:54321/api/orders/1
```

Expect a gateway-side connection/DNS error — `orders-service` (extra `s`) doesn't exist.

Diagnose the way you would in production:

```bash
kubectl get svc -n ecommerce          # confirm the real name is "order-service"
kubectl logs -n ecommerce deployment/gateway-service --tail=50
```

**Fix:**

```bash
kubectl set env deployment/gateway-service -n ecommerce \
  SERVICES_ORDER_URL=http://order-service:9091
```

### 14.2 Service Exists, Selector Is Wrong (Service has zero endpoints)

Edit `k8s/order-service.yaml`, change only the Service's selector:

```yaml
spec:
  selector:
    app: wrong-order-service   # was: order-service
```

Apply just the Service change:

```bash
kubectl apply -f k8s/order-service.yaml
kubectl get svc order-service -n ecommerce        # Service still exists
kubectl get endpoints order-service -n ecommerce  # <none> — this is the tell
```

Gateway calls will now hang/fail even though `kubectl get svc` looks fine — this is the single most common "silent" Kubernetes networking bug.

**Fix:** revert the selector back to `app: order-service` in the file and re-apply.

### 14.3 Wrong `targetPort` (connects, but nothing is listening)

Edit `k8s/order-service.yaml`:

```yaml
ports:
  - name: http
    port: 9091
    targetPort: 9999   # was: 9091 — Order Pod doesn't listen here
```

```bash
kubectl apply -f k8s/order-service.yaml
```

Test from the disposable curl Pod (step 10) — you'll get "connection refused", even though DNS resolves, the Service exists, the selector matches, and endpoints are populated. This isolates "port mapping" as its own failure layer, distinct from DNS/selector/endpoints.

**Fix:** set `targetPort` back to `9091` and re-apply.

---

## 15. Readiness / Liveness Probes (Optional — Requires a Dependency Change)

The reference doc's probe examples (section 33-36) assume Spring Boot Actuator's `/actuator/health/readiness` and `/actuator/health/liveness` endpoints. **Neither `order-service` nor `gateway-service` currently has the Actuator dependency** — so those exact probes will fail out of the box. Two options:

### 15.1 Where Do Probes Go, and Who Calls Them?

**1. Deployment, not Service — and it's not even a stylistic choice.**

`readinessProbe` / `livenessProbe` live inside `spec.template.spec.containers[]` in the **Deployment** (see `k8s/order-service.yaml`) — never in the `Service` object. That's because a probe is per-container config: "how do I check if *this specific container* is alive/ready?" A Deployment's Pod template is the only place that describes containers at all. A Service has no concept of a container — it's just a stable virtual IP plus a label selector (`app: order-service`) that says "route to whatever Pods match this selector and are currently Ready." It doesn't run anything, so there is nowhere in its spec *to* put a probe.

Put differently: the **kubelet** (the agent running on the node that hosts the Pod) is what actually executes the probe, by calling the container directly at its Pod IP — never through the Service. The Service only ever *reads the result* (via the Pod's Ready condition), it never *performs* the check. That's the "why only here."

**2. Who calls `/actuator/health/liveness` and `/actuator/health/readiness`, and how the result is used:**

| | Liveness | Readiness |
|---|---|---|
| Caller | kubelet, on the node hosting the Pod | kubelet, on the node hosting the Pod |
| How | HTTP GET straight to `PodIP:9091/actuator/health/liveness`, on a timer (`periodSeconds`) | Same mechanism, straight to `PodIP:9091/actuator/health/readiness` |
| On failure (past `failureThreshold`) | kubelet kills the container and restarts it per `restartPolicy` | kubelet flips the Pod's `Ready` condition to `False` — the Pod is **not** restarted |
| Who consumes that outcome | Nobody else — it's purely "is this process stuck, kill it" | The **EndpointSlice controller** watches Pod readiness and removes the Pod's IP from the Service's Endpoints/EndpointSlice; **kube-proxy** reprograms `iptables`/IPVS rules from that list, so client traffic simply stops being routed to that Pod |

So the request path in production looks like this for a normal call through the gateway:

```
client → gateway-service (Service, NodePort) → kube-proxy iptables rule
        → only Pods currently listed in order-service's Endpoints
        → i.e. only Pods whose readinessProbe is currently passing
```

Liveness never appears in that path at all — it's a side-channel between kubelet and the container, orthogonal to traffic.

**3. How this is actually used in a production-grade system:**

- **Readiness = "can I take traffic right now?"** Two cases: still starting up (Spring context takes a few seconds to load — Pod is `Running` but held out of rotation until it's actually ready), or temporarily struggling (DB pool exhausted, a downstream call is failing). Either way the Pod is quietly pulled out of the Service's Endpoints — no restart, no lost in-flight requests — and put back the moment the check passes again.
- **Liveness = "is this process dead and needs a kill + restart?"** Only for the case where the app itself is permanently stuck (deadlock, hung thread pool) and will never recover on its own. Golden rule: **never** wire liveness to a downstream dependency like the DB — if the DB hiccups, every Pod's liveness would fail at once and Kubernetes would restart the whole fleet simultaneously, which is worse than the original hiccup. Readiness is the correct probe for "a dependency is down"; liveness is only for "I am broken and restarting me is the fix."
- **Rolling deploys use readiness too:** Kubernetes won't shift traffic to a new Pod, or retire an old one, until the new Pod's readiness probe passes. That sequencing is what makes a rollout zero-downtime.
- Anything that watches Pod readiness — Ingress, a service mesh, another Service — sees the same up-to-date picture, since they all read the one status Kubernetes maintains per Pod.

> **Worked example — does readiness still apply with no custom `HealthIndicator`?** Yes, and here's the concrete version, using Postgres as the DB (this repo actually uses H2, but Postgres makes the failure mode real instead of hypothetical — H2 here is embedded in the same Pod, so "DB down, app fine" can't really happen to it).
>
> Say `order-service` talks to a Postgres Pod over the network. With `spring-boot-starter-data-jpa` + Actuator on the classpath, Actuator auto-creates a `DataSourceHealthIndicator` that runs a trivial query against Postgres whenever health is checked. There are two separate knobs here, and this is the part that's easy to miss:
> 1. `management.endpoint.health.probes.enabled: true` turns the `/actuator/health/readiness` and `/actuator/health/liveness` endpoints **on**.
> 2. What `readiness` actually *checks* is a separate setting. By default it only checks "has my Spring context finished starting" — the DB indicator exists on `/actuator/health`, but is **not** automatically folded into the readiness group.
>
> **Left at the default:** Postgres goes down → `/actuator/health/readiness` still says UP (the app itself is fine) → Kubernetes keeps routing traffic to the Pod → every DB-touching request throws a 500. Kubernetes has no way to know, because readiness never asked about Postgres.
>
> **Opt the DB into readiness:**
> ```yaml
> management:
>   endpoint:
>     health:
>       group:
>         readiness:
>           include: readinessState, db
> ```
> Now Postgres going down flips the `db` indicator to DOWN → `/actuator/health/readiness` returns 503 → kubelet (already polling this on a timer) marks the Pod **not Ready** after `failureThreshold` failures → the Pod drops out of `order-service`'s Endpoints → traffic stops flowing to it. Nothing is killed or restarted — that's liveness's job, and the DB should never be wired into liveness (see the golden rule above). When Postgres recovers, the `db` indicator flips UP, readiness follows, and the Pod rejoins on its own.
>
> If every replica shares the same Postgres and it goes down, all replicas fail readiness together, so the Service ends up with zero endpoints — calls from `gateway-service` fail fast with no Pod to route to, instead of getting silent 500s from Pods that report healthy but aren't. That's the actual payoff of wiring the DB into readiness.

### Option A — Add Actuator (closer to the reference doc)

Add to `order-service/pom.xml` (and optionally `gateway-service/pom.xml`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Then add to `order-service/src/main/resources/application.yml`:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

Then in `k8s/order-service.yaml`, inside the container spec:

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 9091
  initialDelaySeconds: 10
  periodSeconds: 5

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 9091
  initialDelaySeconds: 20
  periodSeconds: 10
```

Rebuild the image (step 6) and re-apply before this takes effect.

### 15.2 Doing the Same for `gateway-service`

`gateway-service` gets the identical treatment, with one difference worth calling out: it's built on **Spring Cloud Gateway**, which is reactive (WebFlux/Netty), not the servlet stack `order-service` uses. Actuator doesn't care — `/actuator/health/readiness` and `/actuator/health/liveness` work exactly the same way on both stacks, because they're backed by Spring Boot's `ApplicationAvailability` mechanism, not by anything servlet- or WebFlux-specific.

Add to `gateway-service/pom.xml` (same dependency, no reactive-specific variant needed):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Add to `gateway-service/src/main/resources/application.yml`:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

Then in `k8s/gateway-service.yaml`, inside the container spec — note the port is **8000**, not 9091, matching `gateway-service`'s own `server.port`:

```yaml
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8000
  initialDelaySeconds: 10
  periodSeconds: 5

livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8000
  initialDelaySeconds: 20
  periodSeconds: 10
```

**Verified locally** (no cluster needed to sanity-check this part): built the jar with `mvn -DskipTests package`, ran it standalone with `java -jar target/gateway-service-0.0.1-SNAPSHOT.jar` (defaults to the `local` profile, which already has `services.order.url` set, so it starts even with `order-service` not running), then:

```bash
curl -s http://localhost:8000/actuator/health/readiness   # {"status":"UP"}
curl -s http://localhost:8000/actuator/health/liveness    # {"status":"UP"}
```

Both returned `200 {"status":"UP"}`, confirming the endpoints exist and work before ever touching Kubernetes. Rebuild the image (step 6) and re-apply `k8s/gateway-service.yaml` before the probes take effect in-cluster.

### Option B — No Code Change (quick, TCP-only probe)

If you just want to *feel* the readiness-vs-liveness mechanics without touching `pom.xml`, use a `tcpSocket` probe against the app port instead — cruder (it only checks the port is open, not that Spring's context finished loading), but requires zero code changes:

```yaml
readinessProbe:
  tcpSocket:
    port: 9091
  initialDelaySeconds: 10
  periodSeconds: 5
```

Either way, the exercise from doc section 36 still works: watch `kubectl get pods -n ecommerce` (Pod shows `Running` but not `Ready` while the probe is failing) vs. `kubectl get endpoints order-service -n ecommerce` (an unready Pod's IP briefly disappears from endpoints during startup, or under `-w` if you scale up and watch closely).

---

## 16. Local vs. Kubernetes Profiles — Already Done, Just Verify It

Stage 3's config split (reference doc section 40-42) is **already implemented** in this repo:

- `gateway-service/src/main/resources/application-local.yml` → `services.order.url: http://localhost:9091`
- `gateway-service/src/main/resources/application-k8s.yml` → `services.order.url: http://order-service:9091`
- `gateway-service/src/main/resources/application.yml` defaults `spring.profiles.active: local`, overridden to `k8s` by the `SPRING_PROFILES_ACTIVE` env var in `k8s/gateway-service.yaml`

To see this in action locally (outside Minikube):

```bash
# terminal 1
cd order-service && mvn spring-boot:run

# terminal 2
cd gateway-service && mvn spring-boot:run
curl http://localhost:8000/api/orders/1
```

This works because the `local` profile is active by default and points at `localhost:9091`. The exact same gateway JAR, deployed into Minikube with `SPRING_PROFILES_ACTIVE=k8s`, points at `order-service:9091` instead — no code change between environments, only configuration.

> **Note:** the poms target `<java.version>17</java.version>`, but this machine's default `java`/`mvn` resolve to JDK 24. That's fine — `javac` targeting release 17 from a newer JDK works — but if `mvn spring-boot:run` ever fails with a Java-version-related error, run `mvn -v` to confirm which JDK Maven picked up, or point `JAVA_HOME` at a 17 install.

---

## Part B — Optional Stretch: Add `product-service` and `inventory-service`

Skip this section if you just want the core Stage 3 lab (Part A already covers every concept). This part is genuinely extra work because, unlike `order-service`, **neither service has a Dockerfile or a REST controller yet** — only an empty `@SpringBootApplication` class.

### 17.1 Add a Dockerfile

Same pattern as `order-service/Dockerfile`, just with a different exposed port.

`product-service/Dockerfile` (port `8081`):

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`inventory-service/Dockerfile` — identical, but `EXPOSE 8082`.

### 17.2 Add a Minimal Controller

Something that at least proves connectivity end-to-end, mirroring `OrderController`'s `servedBy` pattern. Example for Product:

```java
package com.tip.ecommerce.product.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {

    @GetMapping("/{id}")
    public java.util.Map<String, Object> getProduct(@PathVariable Long id) {
        return java.util.Map.of(
                "productId", id,
                "name", "Sample Product",
                "servedBy", System.getenv().getOrDefault("HOSTNAME", "local")
        );
    }
}
```

Same idea for `InventoryController` under `/inventory`.

### 17.3 Kubernetes Manifests

`k8s/product-service.yaml` and `k8s/inventory-service.yaml`, same shape as `k8s/order-service.yaml` in section 4, but with ports `8081` and `8082` respectively, and 1 replica each is enough to prove the idea.

### 17.4 Add Gateway Routes

In `gateway-service/src/main/resources/application.yml`, add alongside the existing `order-service-v1`/`v2` routes:

```yaml
- id: product-service
  uri: ${services.product.url}
  predicates:
    - Path=/api/products/**
  filters:
    - StripPrefix=1

- id: inventory-service
  uri: ${services.inventory.url}
  predicates:
    - Path=/api/inventory/**
  filters:
    - StripPrefix=1
```

And extend `application-local.yml` / `application-k8s.yml` with `services.product.url` / `services.inventory.url` pointing at `localhost:8081`/`8082` and `product-service:8081`/`inventory-service:8082` respectively — same pattern already used for `services.order.url`.

Once this is in place, everything from Part A (build image, apply, verify endpoints, delete-pod exercise, selector-break exercise, etc.) applies identically to these two services.

---

## Part C — Production-Grade: DNS Name, Certificates & SSL Termination at the Gateway

There's no reference-doc chapter for this yet — it's new ground, built the same way the rest of this lab was: real tools, no shortcuts, mapped explicitly to what changes (and what doesn't) when this becomes a real EKS deployment.

### 18. The Production Mental Model

Real production, terminating TLS at the edge component (which is exactly what an API Gateway is):

```text
Client
  |
Route 53 (DNS: api.yourcompany.com)
  |
NLB / LoadBalancer  (routes TCP, doesn't see TLS)
  |
Gateway Pods — TLS terminated HERE
  | (cert lifecycle owned by cert-manager, backed by ACM or Let's Encrypt/ACME)
  v
plain HTTP inside the cluster to order-service, product-service, ...
```

Local Minikube, same shape, different backends for exactly two pieces (DNS source, CA):

```text
Client (curl / browser on your Mac)
  |
/etc/hosts (DNS: gateway.ecommerce.local)
  |
minikube tunnel  (emulates a cloud LoadBalancer)
  |
gateway-service, type: LoadBalancer
  |
Gateway Pod — TLS terminated HERE
  | (cert lifecycle owned by cert-manager, backed by a local self-signed root CA)
  v
plain HTTP inside the cluster to order-service:9091
```

Everything below this line — the Deployment volume mount, the `server.ssl.*` config, the cert-manager `Issuer`/`Certificate` resources, the renewal behavior — is **identical** to what you'd write for EKS. The only two things that change when you move there:

| Piece | Here (Minikube) | There (EKS) |
|---|---|---|
| DNS | `/etc/hosts` entry | Route 53 record |
| Certificate issuer | cert-manager `Issuer` (kind: `ca`, backed by a self-signed root) | cert-manager `ClusterIssuer` (kind: `acme`, backed by Let's Encrypt or your DNS provider) — or AWS ACM if terminating at an NLB instead of in-pod |
| Edge reachability | `minikube tunnel` | a real cloud Network Load Balancer |

That's the whole point of doing this locally with real tools instead of `curl -k` and a hardcoded IP: the muscle memory transfers.

### 19. Cost Reality Check

Everything in this section is **$0**. No signup, no card, no trial period.

| Component | Tool | Cost |
|---|---|---|
| DNS name | `/etc/hosts` (or `nip.io`, see below) | Free |
| Certificate authority | cert-manager `SelfSigned`/`CA` Issuer | Free |
| Certificate issuance & renewal | cert-manager (open source, CNCF project) | Free |
| Local LoadBalancer emulation | `minikube tunnel` (built into Minikube) | Free |
| Quick manual alternative | `mkcert` (open source) | Free |

**The only thing that ever costs money** is a *real, publicly-registered domain name* — and you only need that when this setup graduates to an actual internet-facing EKS deployment, not for this local lab. When you get there:

- **Cheapest option:** [Cloudflare Registrar](https://www.cloudflare.com/products/registrar/) — sells domains at wholesale/at-cost with no markup, typically **~$9–10/year for a `.com`**, no upsells at renewal (unlike GoDaddy/Namecheap, which often discount year one then raise renewal price).
- The certificate itself would *still* be free — cert-manager + Let's Encrypt (ACME) issues real, publicly-trusted certificates for any domain you control, at no cost, indefinitely.

So: nothing to buy right now. Proceed with the free path.

### 20. Pick a Local DNS Name

We'll use `gateway.ecommerce.local` throughout. Any name works as long as it's consistent between the certificate's `dnsNames` (section 21) and what you type in `curl`/the browser (section 23-24) — a TLS handshake fails if the hostname you connect to doesn't match a name on the certificate.

You won't add the `/etc/hosts` line until section 23, because it needs to point at an IP address `minikube tunnel` hasn't handed out yet. Two options when you get there:

- **`/etc/hosts` (what this lab uses):** requires `sudo` to edit, but works fully offline and needs nothing installed.
- **Alternative — skip `/etc/hosts` entirely:** use [`nip.io`](https://nip.io) or [`sslip.io`](https://sslip.io), free wildcard DNS services that resolve `anything.<ip>.nip.io` to `<ip>` automatically, no config anywhere. E.g. if your tunnel IP turns out to be `127.0.0.1`, you'd just use `gateway.127.0.0.1.nip.io` everywhere instead of editing system files or picking a `.local` name. Substitute it for `gateway.ecommerce.local` in every command below if you'd rather not touch `/etc/hosts`.

### 21. Certificates

Two options. **Option A (cert-manager)** is the actual production pattern — automated issuance and renewal, nothing to remember to rotate. **Option B (mkcert)** is a 60-second manual alternative if you just want to see TLS working before investing in cert-manager, or if cert-manager's webhook won't come up on your machine for some reason. Pick one; both produce a Kubernetes `Secret` with the exact same shape (`tls.crt` / `tls.key` keys), so section 22's Gateway config is identical either way.

#### Option A — cert-manager (Recommended, Production-Grade)

**Install cert-manager** (v1.21.1 is current as of this writing — check [cert-manager.io/docs/installation](https://cert-manager.io/docs/installation/) if it's been a while):

```bash
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.21.1/cert-manager.yaml
```

Wait for all three components to be `Running` and `1/1`:

```bash
kubectl get pods -n cert-manager -w
```

(Ctrl+C once you see `cert-manager`, `cert-manager-cainjector`, and `cert-manager-webhook` all ready — this can take a minute, the webhook needs its TLS bootstrap to finish first.)

**Create a local root CA, then an Issuer backed by it.** This is a standard two-tier cert-manager pattern: a `SelfSigned` Issuer only exists to bootstrap one CA certificate; a second `Issuer` (kind `ca`) then uses that CA to sign every real certificate — which is exactly the shape of a real internal PKI.

`k8s/tls/root-ca.yaml`:

```yaml
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: selfsigned-bootstrap
  namespace: ecommerce
spec:
  selfSigned: {}
---
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: ecommerce-root-ca
  namespace: ecommerce
spec:
  isCA: true
  commonName: ecommerce-local-root-ca
  secretName: ecommerce-root-ca-secret
  duration: 87600h   # 10 years — this is your personal local CA, not a public one
  privateKey:
    algorithm: ECDSA
    size: 256
  issuerRef:
    name: selfsigned-bootstrap
    kind: Issuer
---
apiVersion: cert-manager.io/v1
kind: Issuer
metadata:
  name: ecommerce-ca-issuer
  namespace: ecommerce
spec:
  ca:
    secretName: ecommerce-root-ca-secret
```

> In a multi-namespace/multi-team production cluster you'd typically use `ClusterIssuer` instead of `Issuer` so every namespace can request certs from one shared CA. We're using the namespaced `Issuer` here because everything in this lab lives in `ecommerce` — same concept, smaller blast radius.

**Request the Gateway's actual certificate**, signed by that CA:

`k8s/tls/gateway-certificate.yaml`:

```yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: gateway-tls
  namespace: ecommerce
spec:
  secretName: gateway-tls-secret
  duration: 2160h       # 90 days — same lifetime convention as Let's Encrypt
  renewBefore: 720h     # cert-manager renews automatically ~30 days before expiry
  dnsNames:
    - gateway.ecommerce.local
  issuerRef:
    name: ecommerce-ca-issuer
    kind: Issuer
```

Apply both and verify:

```bash
kubectl apply -f k8s/tls/root-ca.yaml
kubectl apply -f k8s/tls/gateway-certificate.yaml

kubectl get certificate -n ecommerce
kubectl get secret gateway-tls-secret -n ecommerce
```

`kubectl get certificate` should show `READY: True` within a few seconds. If it doesn't, `kubectl describe certificate gateway-tls -n ecommerce` and read the Events — cert-manager surfaces the exact failure there (usually a webhook that isn't ready yet; wait 30s and check again).

#### Option B — mkcert (Quick, Manual Renewal)

```bash
brew install mkcert
mkcert -install                                          # installs a local CA into your Mac + browser trust stores
mkcert -cert-file gw.crt -key-file gw.key gateway.ecommerce.local

kubectl create secret tls gateway-tls-secret \
  -n ecommerce \
  --cert=gw.crt --key=gw.key
```

That's it — skip the `Issuer`/`Certificate` YAML above entirely; you already have `gateway-tls-secret` with the right shape. The tradeoff: mkcert certs are long-lived and nothing renews them automatically — when one eventually expires, you re-run the two `mkcert`/`kubectl create secret` commands by hand. That manual step is precisely the gap cert-manager exists to close, which is why Option A is the one to use if you want the "production-grade" property, not just working HTTPS.

### 22. Terminate TLS Inside Spring Cloud Gateway

Spring Boot 3.1+ (this repo is on 3.5.9) can load certificates directly from PEM files — no keystore/PKCS12 conversion needed, and it reads the exact `tls.crt`/`tls.key` filenames a Kubernetes TLS secret mounts by default.

Add to `gateway-service/src/main/resources/application-k8s.yml` (profile-specific settings override the base `application.yml`, so this only applies in-cluster — your `local` profile keeps running plain HTTP on 8000 for the `mvn spring-boot:run` workflow from section 16):

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    certificate: file:/etc/tls/tls.crt
    certificate-private-key: file:/etc/tls/tls.key
```

Update `k8s/gateway-service.yaml` to mount the secret and switch to a `LoadBalancer` Service on 8443 (replacing the `NodePort`/8000 config from section 5 — Part A's plain-HTTP path stops applying once you make this change, which is expected):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-service
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway-service
  template:
    metadata:
      labels:
        app: gateway-service
    spec:
      containers:
        - name: gateway-service
          image: gateway-service:stage3
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8443
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: k8s
          volumeMounts:
            - name: tls
              mountPath: /etc/tls
              readOnly: true
      volumes:
        - name: tls
          secret:
            secretName: gateway-tls-secret
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: ecommerce
spec:
  type: LoadBalancer
  selector:
    app: gateway-service
  ports:
    - name: https
      port: 8443
      targetPort: 8443
```

Rebuild the image (it needs the updated `application-k8s.yml`) and roll it out:

```bash
minikube image build -t gateway-service:stage3 ./gateway-service
kubectl apply -f k8s/gateway-service.yaml
kubectl rollout status deployment/gateway-service -n ecommerce
```

If the Pod crash-loops, `kubectl logs -n ecommerce deployment/gateway-service` first — the most common cause is the Secret not mounting yet (order-of-operations: apply section 21's cert-manager resources *before* this Deployment change) or a typo in the mount path vs. `server.ssl.certificate`.

### 23. Expose the Gateway and Wire Up DNS

`LoadBalancer`-type Services don't get an external address on their own in Minikube — `minikube tunnel` provides that (it's Minikube's stand-in for a cloud NLB). Open a **separate terminal** for this — it runs in the foreground and needs `sudo`:

```bash
minikube tunnel
```

In your original terminal, watch for an external address to appear:

```bash
kubectl get svc gateway-service -n ecommerce -w
```

Once `EXTERNAL-IP` is no longer `<pending>`, capture it — **don't assume it's `127.0.0.1`**; the exact address `minikube tunnel` assigns depends on your Minikube version/driver, so look it up rather than guessing:

```bash
GW_IP=$(kubectl get svc gateway-service -n ecommerce -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "Gateway external IP: $GW_IP"
```

Sanity-check it's actually reachable before touching DNS:

```bash
curl -k "https://$GW_IP:8443/api/orders/1"
```

(`-k` here just skips certificate *hostname/trust* validation for this one raw-IP sanity check — section 24 replaces it with real trust.) If that doesn't respond, the tunnel isn't routing yet; give it a few more seconds or re-run `minikube tunnel`.

Once it works, add the DNS name:

```bash
echo "$GW_IP gateway.ecommerce.local" | sudo tee -a /etc/hosts
```

> **If `minikube tunnel` doesn't cooperate on your setup:** fall back to the NodePort approach from section 9 (`minikube service gateway-service -n ecommerce --url`) instead of `LoadBalancer`. You'll get a `127.0.0.1:<random-port>` tunnel each time instead of a fixed `8443`, so the DNS name will need the port appended in each command (`https://gateway.ecommerce.local:<that-port>/...`) rather than the clean `:8443` used below — TLS termination itself works identically either way; only the "stable DNS + fixed port" convenience is lost.

### 24. Trust the Local CA & Test HTTPS End-to-End

Export the root CA's public certificate (this is safe to export — it's the *public* cert, not the private key):

```bash
kubectl get secret ecommerce-root-ca-secret -n ecommerce -o jsonpath='{.data.tls\.crt}' | base64 -d > ecommerce-local-ca.crt
```

**Option A — trust it just for `curl`** (no system changes):

```bash
curl --cacert ecommerce-local-ca.crt https://gateway.ecommerce.local:8443/api/orders/1
```

**Option B — trust it system-wide** (closest to a real "green padlock" experience — Chrome/Safari will show the connection as fully secure, no warnings, matching what a real publicly-trusted cert looks like):

```bash
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain \
  ecommerce-local-ca.crt
```

Then plain `curl` (no flags) and your browser both trust it:

```bash
curl https://gateway.ecommerce.local:8443/api/orders/1
curl -H "X-API-Version: 2" https://gateway.ecommerce.local:8443/api/orders/1
```

Open `https://gateway.ecommerce.local:8443/api/orders/1` in a browser — you should see a valid padlock with no warning.

To remove that trust later (e.g. when you tear this lab down):

```bash
sudo security delete-certificate -c "ecommerce-local-root-ca" /Library/Keychains/System.keychain
```

> If mkcert (Option B in section 21) is what you used instead, skip the `kubectl get secret ecommerce-root-ca-secret` step — `mkcert -install` already put its CA in your trust stores, so `curl`/the browser already trust `gateway-tls-secret` without any extra step here.

### 25. Prove the Certificate Lifecycle Is Actually Automated

This is the part that makes it "production-grade" rather than "HTTPS once": cert-manager, not you, owns this certificate going forward.

```bash
kubectl describe certificate gateway-tls -n ecommerce   # shows Renewal Time in the Status
```

Force a real end-to-end proof — delete the Secret and watch cert-manager notice and recreate it without you doing anything:

```bash
kubectl delete secret gateway-tls-secret -n ecommerce
kubectl get certificate gateway-tls -n ecommerce -w
```

Within seconds, `READY` flips `False` → `True` again and the Secret reappears — cert-manager detected the missing Secret backing its `Certificate` resource and re-issued it automatically, signed by the same CA. In EKS with an ACME `ClusterIssuer`, this exact reconciliation loop is what re-requests a certificate from Let's Encrypt before the old one expires — nobody gets paged for an expired cert because nobody has to remember to rotate one by hand.

> Note the Gateway Pod itself won't pick up the *new* cert bytes until it restarts (it read the mounted file once at startup) — `kubectl rollout restart deployment/gateway-service -n ecommerce` if you want to confirm the new cert is actually being served. A real production setup would add a sidecar or `reloader`-style controller to avoid even that manual restart; that's a good next research topic once this feels comfortable, not something to build into this lab.

---

## 26. Troubleshooting Cheat Sheet

Use this order — don't guess-edit YAML randomly.

| Step | Command | Rules out |
| --- | --- | --- |
| 1. Is the Gateway Pod even up? | `kubectl get pods -n ecommerce -l app=gateway-service` + `kubectl logs -n ecommerce deployment/gateway-service` | Gateway crash/startup failure |
| 2. Does the route match? | Check `Path=`/`Header=` predicates in `gateway-service/src/main/resources/application.yml` | Route predicate mismatch → 404 from gateway itself |
| 3. Does the target Service exist? | `kubectl get svc order-service -n ecommerce` | Typo'd service name |
| 4. Does the Service have endpoints? | `kubectl get endpoints order-service -n ecommerce` | Selector/label mismatch, or no Pods ready |
| 5. Are the Pods actually ready? | `kubectl get pods -n ecommerce -l app=order-service` | Slow startup, crash loop, failing readiness probe |
| 6. Can another Pod reach the Service directly? | `kubectl run curl-test --rm -it -n ecommerce --image=curlimages/curl -- curl http://order-service:9091/orders/v1/1` | Isolates "gateway problem" vs. "networking problem" |
| 7. What does the target app itself say? | `kubectl logs -n ecommerce <order-pod-name>` | App-level exception vs. infra issue |

| Symptom | Likely cause |
| --- | --- |
| Gateway unreachable at all | Gateway Pod/Service itself down |
| Gateway responds 404 | Route predicate doesn't match your path/headers |
| "Unknown host" / DNS error | Wrong Service name, or cross-namespace without qualifying it |
| Service exists, `get endpoints` shows `<none>` | Selector ≠ Pod labels, or no Pod is Ready |
| "Connection refused" from inside the cluster | `targetPort` doesn't match the app's actual listening port |
| Works from the disposable curl Pod, fails via Gateway | Bug is in gateway config/route, not networking |
| Works from IntelliJ locally, fails in Minikube | Check `SPRING_PROFILES_ACTIVE`, and that the `k8s` profile file has the right Service DNS name/port |

### Part C (TLS/DNS) Symptoms

| Symptom | Likely cause |
| --- | --- |
| `kubectl get certificate` stuck `READY: False` | `kubectl describe certificate gateway-tls -n ecommerce` and read Events — usually the webhook wasn't ready yet, or the `issuerRef` name/kind doesn't match an existing `Issuer` |
| `gateway-service` `EXTERNAL-IP` stuck `<pending>` | `minikube tunnel` isn't running, or was closed — it must stay running in its own terminal |
| Gateway Pod `CrashLoopBackOff` right after enabling TLS | `kubectl logs` — almost always the `gateway-tls-secret` volume isn't mounted yet (apply cert-manager resources before this Deployment) or `server.ssl.certificate` path doesn't match the mount path |
| `curl: SSL certificate problem: unable to get local issuer certificate` | Expected without `--cacert`/system trust — see section 24; this means TLS itself is working, just untrusted |
| `curl: (60) SSL: no alternative certificate subject name matches target host name` | You connected to a hostname not listed in the cert's `dnsNames` — must be exactly `gateway.ecommerce.local` (or whatever you put in the `Certificate`/`mkcert` command), not `localhost` or the raw IP |
| Browser still shows "Not Secure" after Option B | macOS requires the cert be added as `trustRoot` in the *System* keychain specifically (not login keychain) — re-check the `security add-trusted-cert` command used `/Library/Keychains/System.keychain` |

### Extra useful commands

```bash
kubectl describe pod <pod-name> -n ecommerce     # events: image pull errors, probe failures, OOMKilled, etc.
kubectl describe svc order-service -n ecommerce  # confirms selector + endpoint list in one place
kubectl get events -n ecommerce --sort-by=.lastTimestamp   # chronological view of everything that happened
kubectl top pods -n ecommerce                    # CPU/memory (needs metrics-server: `minikube addons enable metrics-server`)
kubectl rollout restart deployment/gateway-service -n ecommerce   # force a restart after a config/env change
kubectl rollout status deployment/order-service -n ecommerce      # watch a rollout finish
```

---

## 27. Cleanup

Reversible, lowest-impact first:

```bash
# Just scale everything to zero (keeps objects, frees resources)
kubectl scale deployment order-service gateway-service -n ecommerce --replicas=0
```

If you did Part C, stop the foreground `minikube tunnel` process (Ctrl+C in its terminal) and undo the two host-level changes it's not Kubernetes' job to clean up:

```bash
# Remove the /etc/hosts line (edit the file, or on macOS/Linux):
sudo sed -i '' '/gateway\.ecommerce\.local/d' /etc/hosts

# Remove system-wide CA trust, if you did section 24 Option B:
sudo security delete-certificate -c "ecommerce-local-root-ca" /Library/Keychains/System.keychain
```

Remove what this lab created but keep Minikube itself running:

```bash
kubectl delete namespace ecommerce

# If you installed cert-manager for Part C and don't need it for anything else:
kubectl delete -f https://github.com/cert-manager/cert-manager/releases/download/v1.21.1/cert-manager.yaml
```

Stop the whole cluster (keeps it on disk, fast to resume with `minikube start`):

```bash
minikube stop
```

Fully delete the cluster (only if you want a completely clean slate — this discards everything, including the images you built with `minikube image build`):

```bash
minikube delete
```

---

## 28. Completion Checklist

**Setup**
- [ ] `docker info`, `minikube status`, `kubectl get nodes` all healthy
- [ ] `kubectl config current-context` prints `minikube`

**Core understanding**
- [ ] I can explain Pod vs. Service in my own words
- [ ] I understand why Pod IPs must never be hard-coded
- [ ] I understand `port` vs. `targetPort`
- [ ] I understand Service selector vs. Pod labels
- [ ] I understand why this repo doesn't use Eureka on Kubernetes

**Part A — deployed & tested**
- [ ] `ecommerce` namespace created
- [ ] `order-service` running with 2 replicas, `ClusterIP`
- [ ] `gateway-service` running with 1 replica, `NodePort`, `SPRING_PROFILES_ACTIVE=k8s` (becomes `LoadBalancer`/`8443` if you do Part C)
- [ ] `curl .../api/orders/1` returns `apiVersion: v1`
- [ ] `curl -H "X-API-Version: 2" .../api/orders/1` returns `apiVersion: v2`
- [ ] Proved DNS resolution from a disposable `curl-test` Pod
- [ ] Watched `servedBy` change across repeated calls
- [ ] Deleted a Pod, watched Kubernetes replace it, confirmed Gateway config never changed
- [ ] Scaled Order Service 2 → 4 → 2, confirmed endpoints update automatically

**Failure drills**
- [ ] Broke the Service name via `kubectl set env` and fixed it
- [ ] Broke the selector and fixed it
- [ ] Broke `targetPort` and fixed it

**Optional**
- [ ] Added readiness/liveness probes (Actuator or `tcpSocket`)
- [ ] Ran the same gateway JAR locally with `local` profile vs. in-cluster with `k8s` profile
- [ ] (Stretch) Brought `product-service` and/or `inventory-service` into the cluster

**Part C — DNS, TLS & SSL termination**
- [ ] cert-manager installed and healthy (`cert-manager`, `cert-manager-cainjector`, `cert-manager-webhook` all `Running`)
- [ ] Local root CA `Issuer` + `Certificate` created; `ecommerce-root-ca-secret` exists
- [ ] Gateway's own `Certificate` (`gateway-tls`) shows `READY: True`; `gateway-tls-secret` exists
- [ ] Gateway pod terminates TLS itself on `8443` via `server.ssl.certificate`/`certificate-private-key` (no keystore conversion needed)
- [ ] `gateway-service` is `type: LoadBalancer`, reachable via `minikube tunnel`
- [ ] `gateway.ecommerce.local` resolves via `/etc/hosts` to the tunnel's actual `EXTERNAL-IP` (looked up, not assumed)
- [ ] `curl --cacert ecommerce-local-ca.crt https://gateway.ecommerce.local:8443/api/orders/1` succeeds
- [ ] (Option B) System-wide CA trust added; browser shows a valid padlock with no warnings
- [ ] Deleted `gateway-tls-secret` and watched cert-manager re-issue it automatically — proved the lifecycle is automated, not manual
- [ ] I can explain what changes (DNS source, Issuer backend, edge reachability) vs. what stays identical when this moves to EKS
- [ ] I know the one thing that costs money later (a real domain, ~$9–10/yr) and that certificates themselves stay free either way

---

## 29. Next Stage

Once this is comfortable, the natural next question — same as the reference doc's section 67 — is what happens when the **Gateway itself** has multiple replicas behind that same TLS-terminating LoadBalancer, and where a real cloud NLB/ALB and Route 53 fit in once this leaves Minikube. That's Stage 4: **Load Balancing, Gateway Scaling & High Availability**.

---

## Part D — Ingress-Based Routing (What Most Companies Actually Use)

Everything in Parts A-C exposes `gateway-service` directly (`NodePort` in Part A, `LoadBalancer` in Part C). That works, and is a completely legitimate pattern — but in most real companies, including yours, the more common setup adds one more layer in front: a Kubernetes **Ingress**. This section covers what that actually is, why it's not a replacement for the Spring Cloud Gateway routing you already built, and exactly what changes (and — the short answer — mostly doesn't change) in this repo to add it.

### 30.1 Ingress vs. API Gateway — Two Different Jobs, Often Both Present

These get conflated because both involve "routing," but they solve different problems at different layers:

| | Ingress | API Gateway (`gateway-service` in this repo) |
|---|---|---|
| **What it is** | A Kubernetes-native API object (`kind: Ingress`), reconciled by a separate **Ingress Controller** (nginx, Traefik, AWS Load Balancer Controller, ...) that runs as its own Pods and either configures a real reverse proxy or provisions a cloud LB | Ordinary application code (Spring Cloud Gateway here) — just another Deployment/Pod like `order-service`, nothing Kubernetes-special about it |
| **What it routes on** | Hostname and URL path — infrastructure-level HTTP routing | Anything the app can inspect: headers (`X-API-Version`), request body, auth tokens, custom business logic |
| **Typical job** | Cheap, uniform edge routing: `api.company.com/orders/*` → `order-service`, `api.company.com/products/*` → `product-service`, one shared TLS cert for everything behind one hostname | The smarter, app-aware stuff a plain Ingress controller doesn't do out of the box: your `v1`/`v2` header-based split is a perfect example — vanilla nginx Ingress has no concept of that without custom annotations/Lua |
| **Who typically owns it** | Platform/infra team, one shared instance per cluster | The team that owns the services behind it |

**In practice, companies commonly run both, stacked**: Ingress at the very edge (TLS termination, hostname routing across many unrelated apps sharing one cluster) forwarding into one or more API Gateways, which then do the finer-grained, app-aware routing. Ingress replacing your Gateway entirely would mean losing the header-based `v1`/`v2` routing (or reimplementing it as much clunkier Ingress-controller-specific annotations) — that's not usually the goal. Adding Ingress **in front of** the existing Gateway is.

### 30.2 What Changes In This Repo — Spoiler: No Java Code

This is the important part to internalize: **Ingress is pure Kubernetes infrastructure config. It sits in front of the `gateway-service` Service and forwards traffic to it, exactly as if a browser had called `gateway-service` directly.** Nothing about `OrderController`, `application.yml`'s route predicates, or the `X-API-Version` header logic changes at all — Spring Cloud Gateway has no idea an Ingress exists.

What *does* change:

| Change | Why |
|---|---|
| Enable an Ingress Controller in Minikube (not present by default) | An `Ingress` object is inert on its own — it's just a declared *intent*; some controller has to be running to actually watch it and configure a real proxy |
| Add a new `k8s/ingress.yaml` manifest | Declares the host/path rules pointing at `gateway-service` |
| `gateway-service`'s Service `spec.type` can move from `NodePort` back to `ClusterIP` (optional but recommended) | Once Ingress is the external entry point, `gateway-service` no longer needs to be independently reachable from outside — same "only the true edge component gets external exposure" rule from section 4.2, except now Ingress is that edge component, not the Gateway's own Service |

### 30.3 Install the Ingress Controller

```bash
minikube addons enable ingress
```
*Minikube ships an `ingress-nginx` controller as an addon — this deploys it into a new `ingress-nginx` namespace. Without this, creating an `Ingress` resource does nothing; there's no controller watching for it.*

```bash
kubectl get pods -n ingress-nginx -w
```
*Watch until the controller Pod(s) show `Running`/`1/1` — Ctrl+C once ready. First-time pull can take a minute.*

### 30.4 Create the Ingress Resource

`k8s/ingress.yaml`:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: gateway-ingress
  namespace: ecommerce
spec:
  ingressClassName: nginx
  rules:
    - host: gateway.ecommerce.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: gateway-service
                port:
                  number: 8000
```

Field-by-field, the genuinely new pieces vs. everything you already know from sections 4-5:

| Field | What it does |
|---|---|
| `spec.ingressClassName: nginx` | Tells Kubernetes *which* Ingress Controller should handle this object — you could have multiple controllers installed; this pins it to the `ingress-nginx` one the addon just installed. |
| `spec.rules[].host` | The hostname this rule matches — same `/etc/hosts` pattern as Part C section 20, just now resolving to the Ingress controller's address instead of the Gateway Pod's directly. |
| `spec.rules[].http.paths[].path` + `pathType: Prefix` | The URL path this rule matches (`/` = everything) — this is the "path-based routing" piece; a second `rules[].host` block with a different `host`/`path` is how you'd route `product.ecommerce.local` to a different Service entirely. |
| `backend.service.name` / `port.number` | Standard Service reference — identical concept to every `uri: ${services.order.url}` you've already wired up, just expressed as native Kubernetes config instead of Spring Cloud Gateway YAML. |

### 30.5 Apply & Test (Do This Now)

```bash
kubectl apply -f k8s/ingress.yaml
```
*Creates the Ingress object; the `ingress-nginx` controller picks it up within a few seconds and reconfigures itself.*

```bash
kubectl get ingress -n ecommerce
```
*Shows the Ingress and, once ready, an `ADDRESS` — the IP the controller is reachable at.*

Same networking caveat as `NodePort` in section 5.3 and `LoadBalancer` in Part C section 23 applies here too — `ingress-nginx`'s own Service is typically `LoadBalancer`-typed, so on the `docker` driver on macOS you need the tunnel:

```bash
minikube tunnel
```
*Run in its own terminal, same as Part C section 23 — required for the controller to actually get a reachable external address on this driver/OS combo.*

```bash
GW_IP=$(kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
echo "$GW_IP gateway.ecommerce.local" | sudo tee -a /etc/hosts
```
*Same DNS wiring pattern as Part C section 23 — capture the real assigned address, don't assume `127.0.0.1`, and register the hostname locally.*

```bash
curl http://gateway.ecommerce.local/api/orders/101
curl -H "X-API-Version: 2" http://gateway.ecommerce.local/api/orders/101
```
*Same two calls as section 9/5.3 — should return identical `v1`/`v2` responses as before. This is the actual point of the exercise: the application-level behavior is completely unchanged, only one new infrastructure hop was inserted in front of it.*

### 30.6 A Note on TLS: Ingress vs. What Part C Taught

Part C (sections 18-25) terminates TLS **inside the Gateway Pod itself** — `server.ssl.certificate` in `application-k8s.yml`, a mounted Secret, Spring Boot handling the handshake in-process. That's a legitimate, if less common, pattern — typically reached for when you want mutual-TLS or a zero-trust service mesh where even *internal* hops are encrypted end-to-end.

**In most companies, TLS terminates at the Ingress Controller instead** — simpler, and the far more common default:

```yaml
spec:
  tls:
    - hosts:
        - gateway.ecommerce.local
      secretName: gateway-tls-secret
```

Added under the same `Ingress` resource's `spec`, referencing the exact same `gateway-tls-secret` cert-manager already produces in Part C section 21 — cert-manager can even provision it automatically via an `cert-manager.io/cluster-issuer` annotation directly on the `Ingress` object, no `Certificate` resource needed by hand. With this approach, `gateway-service` goes back to plain HTTP on `8000` internally — **no `server.ssl.*` config, no PEM mount, no code/config change in the Spring Boot app at all** — the Ingress Controller handles encryption for the one hop that's actually crossing an untrusted network (your Mac → the cluster edge), which is the boundary that matters for the overwhelming majority of production setups, including likely how your company's is set up.
