#version 460 core


layout (std140) uniform Matrices
{
	mat4 fixMatrix;
	mat4 projectionMatrix;
	mat4 viewMatrix;
	mat4 vpMatrix;
	mat4 vpfMatrix;
};


layout(location = 1) in vec4 vertex;
layout(location = 2) in vec4 colour;


out vec4 vColour;

void main() {
	vColour = colour;
    vec4 position = vpfMatrix * vertex;
    gl_Position =   position;
}