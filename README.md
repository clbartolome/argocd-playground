# argocd-playground

> [!IMPORTANT]  
> Last tested versions: 
> - OpenShift: 4.18.4
> - OpenShift GitOps: 1.15.1

Workshop details:

[1. Review demo application and create image](#1-review-demo-application-and-create-image)

[2. Deploy into OpenShift using Console](#2-deploy-into-openshift)

[3. Create deploy repository](#3-create-deploy-repository)

[4. Create argo app](#4-create-argo-app)

[5. Helm Review](#5-helm-review)

[6. Kustomize Review](#6-kustomize-review)


- Helm Review
- Kustomize Review
- Vault Review
- App of apps
- App Sets

## Playground Setup

### Pre-Requisites

- Install **OpenShift GitOps** operator (default config)
- Build Ansible Execution Environment Manually:

```sh
cd installation/ansible-navigator
ansible-builder build -t argo-playground-ee:latest
```

### Install

- Open a terminal

- Login into OpenShift

- Access installation->ansible-navigator: `cd installation/ansible-navigator`

- Create environment vars for configuration (**update Bitwarden token**):

```sh
export OPENSHIFT_TOKEN=$(oc whoami --show-token)
export CLUSTER_DOMAIN=$(oc whoami --show-server | sed 's~https://api\.~~' | sed 's~:.*~~')
export BITWARDEN_TOKEN=<BITWARDEN_TOKEN_HERE>
```

- Run installation:

```sh
ansible-navigator run ../install.yaml -m stdout \
    -e "ocp_host=$CLUSTER_DOMAIN" \
    -e "api_token=$OPENSHIFT_TOKEN" \
    -e "bw_token=$BITWARDEN_TOKEN"
```

### Clean-up

- Open a terminal

- Login into OpenShift

- Access installation->ansible-navigator: `cd installation/ansible-navigator`

- Create environment vars for configuration (**update token**):

```sh
export OPENSHIFT_TOKEN=$(oc whoami --show-token)
export CLUSTER_DOMAIN=$(oc whoami --show-server | sed 's~https://api\.~~' | sed 's~:.*~~')
```

- Run cleanup:

```sh
ansible-navigator run ../uninstall.yaml -m stdout \
    -e "ocp_host=$CLUSTER_DOMAIN" \
    -e "api_token=$OPENSHIFT_TOKEN"
```


## 1. Review demo application and create image

Quarkus application that includes the following endpoints:

- **/** - main page
- **/api/info** - application info endpoint
- **/q/dev-ui/welcome** - access quarkus dev tools 
- **/q/health/live**
- **/q/health/ready**

Run locally:

```sh
mvn -f demo-app clean quarkus:dev
```

Build application (native build) and upload into Quay.io

```sh
# Compile and create image
mvn -f demo-app package -Pnative -Dquarkus.native.container-build=true
podman build -f demo-app/src/main/docker/Dockerfile.native -t argo-demo-app:1.0 demo-app

# Review Image
podman images argo-demo-app

# Push into Quay
podman tag argo-demo-app:1.0 quay.io/calopezb/argo-demo-app:1.0
podman push quay.io/calopezb/argo-demo-app:1.0
```

## 2. Deploy into OpenShift

Create a namespace and deploy image:

```sh
# Create project
oc project demo-app

# Deploy application and create a route
oc new-app --image=quay.io/calopezb/argo-demo-app:1.0 --name=demo-app
oc expose svc demo-app
```

Test image:

```sh
# Get route (alternatively use console)
oc get route
```

Escale application and review pod update:

```sh
# Review deployment
oc get deploy

# Scale to 3 replicas
oc scale deploy/demo-app --replicas=3
oc get pods

# Test pods
curl demo-app-demo-app.apps.<domain>/api/info
```

Create a configmap with 'public' configuration:

```sh
# Create CM
oc create configmap demo-app-config --from-literal=APP_NAME="Demo App" --from-literal=APP_VERSION="1.0" --from-literal=APP_ENVIRONMENT="OpenShift (argo-demo-app namespace)" 

# Review CM
oc get cm
oc get cm demo-app-config -o yaml
```

Create a secret with 'sensitive' data:


```sh
# Create CM
oc create secret generic demo-app-sec --from-literal=APP_SECRET=supersecret

# Review CM
oc get secret
oc get secret demo-app-sec -o yaml

# Review Data
echo <secret_value> | base64 -d
```

Inject configurations into pods:

```sh
# Configure cm
oc set env deploy/demo-app --from=cm/demo-app-config

oc get pods

# Review web

# Configure secret
oc set env deploy/demo-app --from=secret/demo-app-sec

oc get pods

# Review web
```

Set liveness and readyness probes:

```sh
# Set liveness
oc set probe deploy/demo-app --liveness --get-url=http://:8080/q/health/live


# Set readiness
oc set probe deploy/demo-app --readiness --get-url=http://:8080/q/health/ready
```

## 3. Create deploy repository

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
# Create temporary folder, clean it up if exits
mkdir /tmp/argo-review
cd /tmp/argo-review

# Clone repo
git clone https://gitea-gitea.apps.<domain>/gitea/demo-argo.git

cd demo-argo
```

Extract deployment and clean up:

```sh
oc get deploy demo-app -o yaml > deploy.yaml

vi deploy.yaml
# Clean up:
# - metadata.namespace
# - metadata.annotations
# - metadata.creationTimestamp
# - metadata.generation
# - metadata.resourceVersion
# - metadata.uid
# - spec.template.metadata.annotations
# - spec.template.metadata.creationTimestamp
# - status

# Update:
# - container image to version 1.0
# - container imagePullPolicy to Always
```

Extract service and clean up:

```sh
oc get svc demo-app -o yaml > svc.yaml

vi svc.yaml
# Clean up:
# - metadata.namespace
# - metadata.annotations
# - metadata.creationTimestamp
# - metadata.resourceVersion
# - metadata.uid
# - spec.clusterIP
# - spec.clusterIPs
# - spec.internalTrafficPolicy
# - spec.ipFamilies
# - spec.ipFamilyPolicy
# - status
```

Extract route and clean up:

```sh
oc get route demo-app -o yaml > route.yaml

vi route.yaml
# Clean up:
# - metadata.namespace
# - metadata.annotations
# - metadata.creationTimestamp
# - metadata.resourceVersion
# - metadata.uid
# - spec.host
# - status
```

Extract cm and clean up:

```sh
oc get cm demo-app-config -o yaml > cm.yaml

vi cm.yaml
# Clean up:
# - metadata.namespace
# - metadata.creationTimestamp
# - metadata.resourceVersion
# - metadata.uid

# Update:
# - variables values
```

Extract secret and clean up:

```sh
oc get secret demo-app-sec -o yaml > secret.yaml

vi secret.yaml
# Clean up:
# - metadata.namespace
# - metadata.creationTimestamp
# - metadata.resourceVersion
# - metadata.uid
```

Commit and push changes:

```sh
git add .
git commit -m "demo app resources"
git push
```

## 4. Create argo app

Access ArgoCD in `https://openshift-gitops-server-openshift-gitops.apps.<domain>/applications`

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-argo
    - Project Name: default
    - Sync Policy: Automatic
    - Mark - Prune Resources 
    - Mark - Self Heal 
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-argo.git
    - Revision: main
    - Path: .
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: demo-argo
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-argo
spec:
  destination:
    name: ''
    namespace: demo-argo
    server: https://kubernetes.default.svc
  source:
    path: '.'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-argo.git
    targetRevision: main
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Play with applications resources (replicas, configuration,...) and see how ArgoCD handles drifts.

## 5. Helm Review

Clone the repository `clbartolome/helm-charts` and review `basic-config` chart:

```sh
cd /tmp/argo-review

git clone https://github.com/clbartolome/helm-charts

cd helm-charts/charts/basic-config

code.
```

Create a `values.yaml`:

```yaml
name: helm-demo

image: quay.io/calopezb/argo-demo-app:1.0

replicas: 2

port: 8080

routeEnabled: true

config:
  enabled: true
  data:
    APP_ENVIRONMENT: "OpenShift (demo-helm)"
    APP_NAME: "Demo App using Helm"
    APP_VERSION: "1.0"

secret:
  enabled: true
  data:
    APP_SECRET: "super secret value from helm"
```

Generate the template using helm:

```sh
helm template -f values.yaml .
```

Deploy Application using helm and play with it:

```sh
# Access helm-demo project
oc project argo-helm

# Install app and review in OpenShift
helm install -f values.yaml helm-demo .

# List releases
helm list

# Upgrade a release
helm upgrade helm-demo . --set replicas=4

# Rollback a release
helm list
helm rollback helm-demo 1

# Uninstall a release
helm uninstall helm-demo
helm list
```

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
cd /tmp/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-helm.git

cd demo-helm
```

Create a `Chart.yaml` file to reference the chart (charts are generated and exposed by github static files):

```yaml
apiVersion: v2
name: demo-app-helm
version: 1.0.0

dependencies:
  - name: basic-config
    version: 1.0.0
    repository: https://clbartolome.github.io/helm-charts
```

Create a values file:

Create a `values.yaml`:

```yaml
basic-config:

  name: demo-app-helm

  image: quay.io/calopezb/argo-demo-app:1.0

  replicas: 3

  port: 8080

  routeEnabled: true

  config:
    enabled: true
    data:
      APP_ENVIRONMENT: "OpenShift (demo-helm)"
      APP_NAME: "Demo App using Helm and Argo"
      APP_VERSION: "1.0"

  secret:
    enabled: true
    data:
      APP_SECRET: "super secret value from helm in argo"
```

Commit and push changes:

```sh
git add .
git commit -m "chart reference and values"
git push
```

Create argoCD application:

- `+ New App`
- General:
  - Application Name: demo-helm
  - Project Name: default
  - Sync Policy: Automatic
  - Mark - Prune Resources 
  - Mark - Self Heal 
- Source:
  - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-helm.git
  - Revision: main
  - Path: .
- Destination:
  - Cluster URL: https://kubernetes.default.svc
  - Namespace: demo-helm
- Helm:
  - Values files: values.yaml
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-helm
spec:
  destination:
    name: ''
    namespace: demo-helm
    server: https://kubernetes.default.svc
  source:
    path: .
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-helm.git
    targetRevision: main
    helm:
      valueFiles:
        - values.yaml
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Play with helm values in gitea repo


## 6. Kustomize Review

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
cd /tmp/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-kustomize.git

cd demo-kustomize
```

Review folder structure and kustomization files.

Test kustomization:

```sh
# Run kustomization for dev environment
kustomize build overlays/dev

# Review resources
```

TODO: Create Argo Application






