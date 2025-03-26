# argocd-playground

> [!IMPORTANT]  
> Last tested versions: 
> - OpenShift: 4.18.4
> - OpenShift GitOps: 1.15.1

Workshop details:

[1. Review demo application and create image](#1-review-demo-application-and-create-image)
[2. Deploy into OpenShift using Console](#2-deploy-into-openshift)
- Generate yaml files and upload to gitea:
    - Create repo
    - Clone repo
    - Extract files (cleaning up)
    - Push files
- Create argo app
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

- Create environment vars for configuration (**update token**):

```sh
export OPENSHIFT_TOKEN=$(oc whoami --show-token)
export CLUSTER_DOMAIN=$(oc whoami --show-server | sed 's~https://api\.~~' | sed 's~:.*~~')
```

- Run installation:

```sh
ansible-navigator run ../install.yaml -m stdout \
    -e "ocp_host=$CLUSTER_DOMAIN" \
    -e "api_token=$OPENSHIFT_TOKEN"
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
curl demo-app-demo-app.<domain>/api/info
```

Create a configmap with 'public' configuration:

```sh
# Create CM
oc create configmap demo-app-config --from-literal=APP_NAME="Demo App" --from-literal=APP_VERSION="1.0" --from-literal=APP_ENVIRONMENT="OpenShift (demo-app namespace)" 

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