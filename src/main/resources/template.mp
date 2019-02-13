verbatimtex
%&latex
\documentclass{article}
\begin{document}
etex
prologues:=3;
beginfig(1);
	draw(0,0)--(6cm,0)--(6cm,6cm)--(0,6cm)--cycle;
	label(btex Hello world! etex, (3cm,3cm));
endfig;
end