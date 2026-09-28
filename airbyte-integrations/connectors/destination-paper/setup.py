from setuptools import find_packages, setup

setup(
    name="destination_paper",
    version="0.1.0",
    description="Idempotent Puente paper destination.",
    packages=find_packages(),
    install_requires=["airbyte-cdk~=0.2"],
    package_data={"": ["*.json"]},
    extras_require={"tests": ["pytest~=7.2"]},
)
