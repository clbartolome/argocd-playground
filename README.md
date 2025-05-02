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

[7. Bitwarden Review](#7-bitwarden-review)

[8. App of Apps Review](#8-app-of-apps-review)

[9. App Sets Review](#9-app-sets-review)

[10. Advanced Sync](#10-advanced-sync)

[11. Blue Green](#11-blue-green)

[12. Canary](#12-canary)


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
export BITWARDEN_TOKEN=0.751a3b7d-493b-466d-ac7f-b2750084ebdd.WmQzCU0avVz5TibxOQiyAf24QHNIqA:y08iPvmZiDFnn8OkpCt6uA==
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
export BITWARDEN_TOKEN=0.751a3b7d-493b-466d-ac7f-b2750084ebdd.WmQzCU0avVz5TibxOQiyAf24QHNIqA:y08iPvmZiDFnn8OkpCt6uA==
```

- Run cleanup:

```sh
ansible-navigator run ../uninstall.yaml -m stdout \
    -e "ocp_host=$CLUSTER_DOMAIN" \
    -e "api_token=$OPENSHIFT_TOKEN" \
    -e "bw_token=$BITWARDEN_TOKEN"
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

# Open http://localhost:8080/q/dev-ui/welcome
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

Access a namespace and deploy image:

```sh
# Access project
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
mkdir -p ~/deleteme/argo-review
cd ~/deleteme/argo-review

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
    - Revision: master
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
    targetRevision: master
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
cd ~/deleteme/argo-review

git clone https://github.com/clbartolome/helm-charts

cd helm-charts/charts/basic-config

code .
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
oc project demo-helm

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
cd ~/deleteme/argo-review
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
  - Revision: master
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
    targetRevision: master
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
cd ~/deleteme/argo-review
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

Create argoCD application:

- `+ New App`
- General:
  - Application Name: demo-kustomize
  - Project Name: default
  - Sync Policy: Automatic
  - Mark - Prune Resources 
  - Mark - Self Heal 
- Source:
  - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-kustomize.git
  - Revision: master
  - Path: overlays/dev
- Destination:
  - Cluster URL: https://kubernetes.default.svc
  - Namespace: argo-kustomize
- Helm:
  - Values files: values.yaml
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-kustomize
spec:
  destination:
    name: ''
    namespace: demo-kustomize
    server: https://kubernetes.default.svc
  source:
    path: overlays/dev
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-kustomize.git
    targetRevision: master
  sources: []
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

## 7. Bitwarden Review

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
cd ~/deleteme/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-bitwardem.git

cd demo-kustomize
```

Review Bitwarden configuration:

- Project for storing secrets
- APP_SECRET (copy ID)
- Machine accounts for connecting from openshift bitwarden operator:
  - Show projects
  - People
  - Access tokens

Review access token in `demo-bitwarden` namespace:

```yaml
oc project demo-bitwarden

# Get secret
oc get secret bw-auth-token
```

Create a Bitwarden secret named `bw-secret.yaml`:

```yaml
apiVersion: k8s.bitwarden.com/v1
kind: BitwardenSecret
metadata:
  labels:
    app.kubernetes.io/name: bitwardensecret
    app.kubernetes.io/instance: bitwardensecret
    app.kubernetes.io/part-of: sm-operator
    app.kubernetes.io/managed-by: kustomize
    app.kubernetes.io/created-by: sm-operator
  name: bw-secret
spec:
  organizationId: <update this value>
  secretName: demo-app-sec
  map:
    - bwSecretId: <update this value>
      secretKeyName: APP_SECRET
  authToken:
    secretName: bw-auth-token
    secretKey: token
```

Commit and push changes:

```sh
git add .
git commit -m "bitwarden secrets"
git push
```

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-bitwarden
    - Project Name: default
    - Sync Policy: Automatic
    - Mark - Prune Resources 
    - Mark - Self Heal 
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-bitwarden.git
    - Revision: master
    - Path: .
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: demo-bitwarden
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-bitwarden
spec:
  destination:
    name: ''
    namespace: demo-bitwarden
    server: https://kubernetes.default.svc
  source:
    path: '.'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-bitwarden.git
    targetRevision: master
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Review resources in argo and generated secrets:

```sh
# Review secrets
oc get secrets

oc get secret demo-app-sec -o yaml

echo <secret-value> | base64 -d 
```

Copy deploy resources for testing this secret:

```sh
# Copy resources
cp -r ../demo-argo/* .

# Remove secret
rm secret.yaml
```

Commit and push changes:

```sh
git add .
git commit -m "bitwarden secrets"
git push
```

Go to ArgoCD and refresh application

## 8. App of Apps Review

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
cd ~/deleteme/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-app-of-apps.git

cd demo-app-of-apps
```

Review repository for managing App of Apps:

- Environments (helm)
- Apps Folder

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-app-of-apps
    - Project Name: default
    - Sync Policy: Automatic
    - Mark - Prune Resources 
    - Mark - Self Heal 
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-app-of-apps.git
    - Revision: master
    - Path: apps
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: openshift-gitops
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-app-of-apps
spec:
  destination:
    name: ''
    namespace: openshift-gitops
    server: https://kubernetes.default.svc
  source:
    path: 'apps'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-app-of-apps.git
    targetRevision: master
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Review deployment in ArgoCD and OpenShift console

Add a test app:

```sh
# Copy prod or dev environment
cp apps/dev.yaml apps/test.yaml

# change environment
sed -i 's/dev/test/g' apps/test.yaml
```

Commit and push changes:

```sh
git add .
git commit -m "test environment"
git push
```

Access ArgoCD and refresh `demo-app-of-apps` to view how test environment is created

## 9. App Sets Review

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
cd ~/deleteme/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-app-sets.git

cd demo-app-sets
```

Review repository for managing App Sets:

- Environments (helm)
- Apps Folder

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-app-sets
    - Project Name: default
    - Sync Policy: Automatic
    - Mark - Prune Resources 
    - Mark - Self Heal 
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-app-sets.git
    - Revision: master
    - Path: app-sets
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: openshift-gitops
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-app-sets
spec:
  destination:
    name: ''
    namespace: openshift-gitops
    server: https://kubernetes.default.svc
  source:
    path: 'app-sets'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-app-sets.git
    targetRevision: master
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Review deployment in ArgoCD and OpenShift console

Add a test app:

```sh
# Copy prod or dev environment
cp environments/dev.json environments/test.json

# change environment
sed -i 's/dev/test/g' environments/test.json
sed -i 's/DEV/TEST/g' environments/test.json
```

Commit and push changes:

```sh
git add .
git commit -m "test environment"
git push
```

Access ArgoCD and refresh `demo-app-sets` to view how test environment is created

## 10. Advanced Sync

Access gitea:

```sh
# Get route
oc get route gitea -n gitea

# Access via web browser (user: gitea | pass: openshift)
```

Clone repository:

```sh
cd ~/deleteme/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-advanced-sync.git

cd demo-advanced-sync
```

Review repository for advanced syncs:

- Waves in cm, first and second deployment
- Pre-sync job
- Post-sync job
- Fail handler job

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-advanced-sync
    - Project Name: default
    - Sync Policy: manual
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-advanced-sync.git
    - Revision: master
    - Path: .
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: demo-advanced-sync
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-advanced-sync
spec:
  destination:
    name: ''
    namespace: demo-advanced-sync
    server: https://kubernetes.default.svc
  source:
    path: '.'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-advanced-sync.git
    targetRevision: master
  project: default
```

Access application and sync manually to review the process:

pre-sync >> cm + first hello >> second hello >> post-sync

Access first deploy and put a wrong image reference in the deployment:

```yaml
  ...
    spec:
      containers:
      - name: hello
        envFrom:
        - configMapRef:
            name: config-file
        image: openshift/hello-openshift-wrong
  ...
```

Sync manually again to review the process:

pre-sync >> cm + first hello >> second hello >> syncfail handler

## 11. Blue Green

Create a new version for new configurarion, open `demo-app/src/main/resources/application.properties` and update:

```
...
app.feature.toggle=true
...
```

Build application 2.0 (native build) and upload into Quay.io

```sh
# Compile and create image
mvn -f demo-app package -Pnative -Dquarkus.native.container-build=true
podman build -f demo-app/src/main/docker/Dockerfile.native -t argo-demo-app:2.0 demo-app

# Review Image
podman images argo-demo-app

# Push into Quay
podman tag argo-demo-app:2.0 quay.io/calopezb/argo-demo-app:2.0
podman push quay.io/calopezb/argo-demo-app:2.0
```

Clone repository:

```sh
cd ~/deleteme/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-blue-green.git

cd demo-blue-green
```

Review repository for blue-green:

- CM (for v1)
- SVCs (same pod selector, argo handles this)
- Rollout (just v1)

Configure a rollout manager at cluster level:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: RolloutManager
metadata:
  name: rollout-manager
  namespace: openshift-gitops
```

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-blue-green
    - Project Name: default
    - Sync Policy: Automatic
    - Mark - Prune Resources 
    - Mark - Self Heal 
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-blue-green.git
    - Revision: master
    - Path: .
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: demo-blue-green
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-blue-green
spec:
  destination:
    name: ''
    namespace: demo-blue-green
    server: https://kubernetes.default.svc
  source:
    path: '.'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-blue-green.git
    targetRevision: master
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Review and test deployment of version 1 in Argo.

Create a configuration map `cm-2-0.yaml` for verion 2:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: demo-app-config-2-0
data:
  APP_ENVIRONMENT: OpenShift (demo-blue-green namespace)
  APP_NAME: Demo App Dev
  APP_VERSION: "2.0"
```

Modify Image in `rollout.yaml` to version 2.0:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: demo-app
spec:
...
  template:
...
    spec:
      containers:
      - name: demo-app
        image: quay.io/calopezb/argo-demo-app:2.0
...
        envFrom:
        - configMapRef:
            name: demo-app-config-2-0
...
```

Commit and push changes:

```sh
git add .
git commit -m "version 2.0 ready"
git push
```

Review:

- App 1.0 still with traffic
- App 2.0 recheable for testing via svc `demo-app-preview`
- Execute rollout in ArgoCD
- Review App 2.0 with real traffic and right configuration
- Review no version 1.0 pods running

## 12. Canary

Clone repository:

```sh
cd ~/deleteme/argo-review
git clone https://gitea-gitea.apps.<domain>/gitea/demo-canary.git

cd demo-canary
```

Review repository for blue-green:

- CM (for v1)
- SVC (single service, argo handles this)
- Rollout (just v1 with manual canary deployment)

Create argoCD application:

- `+ New App`
- General:
    - Application Name: demo-canary
    - Project Name: default
    - Sync Policy: Automatic
    - Mark - Prune Resources 
    - Mark - Self Heal 
- Source:
    - Repository URL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-canary.git
    - Revision: master
    - Path: .
- Destination:
    - Cluster URL: https://kubernetes.default.svc
    - Namespace: demo-canary
- `Create`

Alternatively use this yaml:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: demo-canary
spec:
  destination:
    name: ''
    namespace: demo-canary
    server: https://kubernetes.default.svc
  source:
    path: '.'
    repoURL: http://gitea.gitea.svc.cluster.local:3000/gitea/demo-canary.git
    targetRevision: master
  project: default
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

Review and test deployment of version 1 in Argo.

Create a configuration map `cm-2-0.yaml` for verion 2:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: demo-app-config-2-0
data:
  APP_ENVIRONMENT: OpenShift (demo-canary namespace)
  APP_NAME: Demo App Dev
  APP_VERSION: "2.0"
```

Modify Image in `rollout.yaml` to version 2.0:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: demo-app
spec:
...
  template:
...
    spec:
      containers:
      - name: demo-app
        image: quay.io/calopezb/argo-demo-app:2.0
...
        envFrom:
        - configMapRef:
            name: demo-app-config-2-0
...
```

Commit and push changes:

```sh
git add .
git commit -m "version 2.0 ready"
git push
```

Review:

- Execute rollout in ArgoCD - 10% 50% 100% (test app traffic balance)
- Review no version 1.0 pods running after rollout completed to 100%