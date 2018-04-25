# devops

### local

assumes you're using virtualenv

#### first setup

you may need to:

 - `source bin/activate` your whatever python env

## playbook

to run a playbook:

`ansible-playbook -i hosts gdax-provision.yaml`



## misc

##### execute random ansible command

`ansible -i hosts gdax -m raw  -a "apt-get install -y python2.7 python-simplejson"`


## investigate

 - https://www.digitalocean.com/community/tutorials/how-to-set-up-multi-factor-authentication-for-ssh-on-ubuntu-16-04
 - hardened ansible role 
   - https://github.com/darth-veitcher/ansible-roles
   - https://github.com/openstack/ansible-hardening
 - http://tinkerpop.apache.org/docs/current/recipes/
