export PS1='\u@\h:\W \$ '
alias l='ls -CF'
alias la='ls -A'
alias ll='ls -alF'
alias ls='ls --color=auto'
source /etc/profile.d/bash_completion.sh

export JAVA_HOME=/usr/lib/jvm/java-1.8-openjdk/
export SPARK_HOME=/spark
export PATH=$PATH:/$SPARK_HOME/bin:$JAVA_HOME/bin
export HADOOP_HOME=/hadoop
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$HADOOP_HOME/lib/native