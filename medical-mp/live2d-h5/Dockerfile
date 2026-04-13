FROM docker.m.daocloud.io/nginx:alpine
COPY public/models /usr/share/nginx/html/models
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8090
CMD ["nginx", "-g", "daemon off;"]
